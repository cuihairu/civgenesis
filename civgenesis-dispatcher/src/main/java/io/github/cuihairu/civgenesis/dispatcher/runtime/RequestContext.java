package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RequestContext implements AutoCloseable {
    private final Connection connection;
    private final ConnectionState state;
    private final PayloadCodec codec;
    private final Frame req;
    private final DispatcherRuntime runtime;
    private final long startNanos;
    private final CivSpan span;
    private final AtomicBoolean responded = new AtomicBoolean(false);
    private final AtomicBoolean deferred = new AtomicBoolean(false);
    private ByteBuf copiedRawPayload;

    RequestContext(Connection connection, ConnectionState state, PayloadCodec codec, Frame req, DispatcherRuntime runtime, long startNanos, CivSpan span) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.state = Objects.requireNonNull(state, "state");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.req = Objects.requireNonNull(req, "req");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.startNanos = startNanos;
        this.span = span == null ? CivSpan.noop() : span;
    }

    public long connectionId() {
        return connection.id();
    }

    public long playerId() {
        return state.playerId();
    }

    public long sessionEpoch() {
        return state.sessionEpoch();
    }

    public int msgId() {
        return req.msgId();
    }

    public long seq() {
        return req.seq();
    }

    public DeferredReply defer() {
        if (responded.get()) {
            throw new IllegalStateException("request already responded");
        }
        deferred.set(true);
        return new DeferredReplyImpl(this);
    }

    public boolean isDeferred() {
        return deferred.get();
    }

    public boolean hasResponded() {
        return responded.get();
    }

    public <T> T decode(Class<T> reqType, Frame req) throws Exception {
        return codec.decode(req.msgId(), reqType, req.payload());
    }

    public ByteBuf rawPayload(Frame req) {
        ByteBuf payload = req.payload();
        if (payload == null || !payload.isReadable()) {
            return Unpooled.EMPTY_BUFFER;
        }
        if (runtime.config().rawPayloadMode() == RawPayloadMode.COPY) {
            copiedRawPayload = payload.copy();
            return copiedRawPayload;
        }
        return payload;
    }

    public void reply(Object resp) {
        if (!responded.compareAndSet(false, true)) {
            throw new IllegalStateException("duplicate response");
        }
        try {
            ByteBuf payload = codec.encode(connection.alloc(), resp);
            Frame frame = new Frame(FrameType.RESP, req.msgId(), req.seq(), 0, 0, 0, 0, payload);
            connection.send(frame);
        } catch (Exception e) {
            runtime.onResponseComplete(state, req.seq());
            runtime.onRequestComplete(req.msgId(), false, CivErrorCodes.SERVER_BUSY, System.nanoTime() - startNanos);
            span.recordException(e);
            span.setError("encode/send failed");
            span.close();
            throw new RuntimeException(e);
        }
        runtime.onRequestComplete(req.msgId(), true, 0, System.nanoTime() - startNanos);
        span.close();
        runtime.onResponseComplete(state, req.seq());
    }

    public void error(CivError error) {
        if (!responded.compareAndSet(false, true)) {
            throw new IllegalStateException("duplicate response");
        }
        try {
            ByteBuf payload = codec.encodeError(connection.alloc(), error);
            Frame frame = new Frame(FrameType.RESP, req.msgId(), req.seq(), 0, ProtocolFlags.ERROR, 0, 0, payload);
            connection.send(frame);
        } catch (Exception e) {
            runtime.onResponseComplete(state, req.seq());
            runtime.onRequestComplete(req.msgId(), false, error.code(), System.nanoTime() - startNanos);
            span.recordException(e);
            span.setError("encode/send error failed");
            span.close();
            throw new RuntimeException(e);
        }
        runtime.onRequestComplete(req.msgId(), false, error.code(), System.nanoTime() - startNanos);
        span.setError(error.message());
        span.close();
        runtime.onResponseComplete(state, req.seq());
    }

    public void needLogin() {
        error(CivError.of(CivErrorCodes.NEED_LOGIN, "need login", true));
        if (runtime.config().closeOnNeedLogin()) {
            connection.close();
        }
    }

    @Override
    public void close() {
        if (copiedRawPayload != null) {
            copiedRawPayload.release();
            copiedRawPayload = null;
        }
    }

    private static final class DeferredReplyImpl implements DeferredReply {
        private final RequestContext ctx;
        private final long expectedEpoch;
        private final AtomicBoolean completed = new AtomicBoolean(false);

        private DeferredReplyImpl(RequestContext ctx) {
            this.ctx = ctx;
            this.expectedEpoch = ctx.sessionEpoch();
        }

        @Override
        public boolean reply(Object resp) {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            if (ctx.sessionEpoch() != expectedEpoch) {
                ctx.error(CivError.of(CivErrorCodes.SESSION_EXPIRED, "session expired", true));
                return false;
            }
            ctx.reply(resp);
            return true;
        }

        @Override
        public boolean error(CivError error) {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            if (ctx.sessionEpoch() != expectedEpoch) {
                ctx.error(CivError.of(CivErrorCodes.SESSION_EXPIRED, "session expired", true));
                return false;
            }
            ctx.error(error);
            return true;
        }

        @Override
        public boolean cancel() {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            ctx.error(CivError.of(CivErrorCodes.SESSION_EXPIRED, "session expired", true));
            return true;
        }
    }
}
