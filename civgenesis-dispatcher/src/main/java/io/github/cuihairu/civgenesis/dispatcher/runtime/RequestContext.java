package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.protocol.Compression;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RequestContext implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);
    private final Connection connection;
    private final ConnectionState state;
    private final PayloadCodec codec;
    private final Frame req;
    private final DispatcherRuntime runtime;
    private final long startNanos;
    private final CivSpan span;
    private final long shardKey;
    private final AtomicBoolean responded = new AtomicBoolean(false);
    private final AtomicBoolean deferred = new AtomicBoolean(false);
    private ByteBuf copiedRawPayload;

    RequestContext(Connection connection, ConnectionState state, PayloadCodec codec, Frame req, DispatcherRuntime runtime, long startNanos, CivSpan span, long shardKey) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.state = Objects.requireNonNull(state, "state");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.req = Objects.requireNonNull(req, "req");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.startNanos = startNanos;
        this.span = span == null ? CivSpan.noop() : span;
        this.shardKey = shardKey;
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
        if (req.seq() <= 0) {
            throw new IllegalStateException("deferred reply requires seq > 0");
        }
        deferred.set(true);
        long expectedEpoch = sessionEpoch();
        ConnectionState.DeferredEntry entry = new ConnectionState.DeferredEntry(req.seq(), req.msgId(), shardKey, expectedEpoch, startNanos, span);
        if (!state.registerDeferred(entry)) {
            deferred.set(false);
            throw new IllegalStateException("duplicate deferred request seq=" + req.seq());
        }
        runtime.scheduleTimeout(runtime.config().requestTimeoutMillis(), () -> runtime.onDeferredTimeout(connection, state, entry, System.nanoTime()));
        return new DeferredReplyImpl(this, entry.seq());
    }

    public boolean isDeferred() {
        return deferred.get();
    }

    public boolean hasResponded() {
        return responded.get();
    }

    public <T> T decode(Class<T> reqType, Frame req) throws Exception {
        if (!req.hasFlag(ProtocolFlags.COMPRESS)) {
            return codec.decode(req.msgId(), reqType, req.payload());
        }
        if (state.compression() != Compression.GZIP) {
            throw new IllegalStateException("compressed payload but compression not negotiated");
        }
        ByteBuf decompressed = GzipPayloads.decompress(connection.alloc(), req.payload());
        try {
            return codec.decode(req.msgId(), reqType, decompressed);
        } finally {
            decompressed.release();
        }
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
            long flags = 0;
            if (state.compression() == Compression.GZIP && payload != null && payload.isReadable()) {
                ByteBuf gz = GzipPayloads.compress(connection.alloc(), payload);
                payload.release();
                payload = gz;
                flags |= ProtocolFlags.COMPRESS;
            }
            byte[] bytes = payload == null ? new byte[0] : ByteBufUtil.getBytes(payload, payload.readerIndex(), payload.readableBytes(), false);
            runtime.recordDedupResponse(state, req.seq(), req.msgId(), flags, bytes);
            Frame frame = new Frame(FrameType.RESP, req.msgId(), req.seq(), 0, flags, 0, 0, payload);
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
        maybeLogSlow(true, 0);
        span.close();
        runtime.onResponseComplete(state, req.seq());
    }

    public void error(CivError error) {
        if (!responded.compareAndSet(false, true)) {
            throw new IllegalStateException("duplicate response");
        }
        try {
            ByteBuf payload = codec.encodeError(connection.alloc(), error);
            long flags = ProtocolFlags.ERROR;
            if (state.compression() == Compression.GZIP && payload != null && payload.isReadable()) {
                ByteBuf gz = GzipPayloads.compress(connection.alloc(), payload);
                payload.release();
                payload = gz;
                flags |= ProtocolFlags.COMPRESS;
            }
            byte[] bytes = payload == null ? new byte[0] : ByteBufUtil.getBytes(payload, payload.readerIndex(), payload.readableBytes(), false);
            runtime.recordDedupResponse(state, req.seq(), req.msgId(), flags, bytes);
            Frame frame = new Frame(FrameType.RESP, req.msgId(), req.seq(), 0, flags, 0, 0, payload);
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
        maybeLogSlow(false, error.code());
        span.setError(error.message());
        span.close();
        runtime.onResponseComplete(state, req.seq());
    }

    public void attachPlayer(long playerId, boolean kickExistingSession) {
        runtime.attachPlayer(connection, state, playerId, kickExistingSession);
    }

    public ResumeDecision resume(long lastAppliedPushId) {
        return runtime.resume(connection, state, lastAppliedPushId);
    }

    public long push(int msgId, Object message, long flags) {
        try {
            ByteBuf payload = codec.encode(connection.alloc(), message);
            byte[] bytes = payload == null ? new byte[0] : ByteBufUtil.getBytes(payload, payload.readerIndex(), payload.readableBytes(), false);
            if (payload != null) {
                payload.release();
            }
            long outFlags = flags;
            if (state.compression() == Compression.GZIP && bytes.length > 0) {
                ByteBuf raw = Unpooled.wrappedBuffer(bytes);
                try {
                    ByteBuf gz = GzipPayloads.compress(connection.alloc(), raw);
                    bytes = ByteBufUtil.getBytes(gz, gz.readerIndex(), gz.readableBytes(), false);
                    gz.release();
                    outFlags |= ProtocolFlags.COMPRESS;
                } finally {
                    raw.release();
                }
            }
            return runtime.push(connection, state, msgId, outFlags, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setCompression(Compression compression) {
        state.compression(compression);
    }

    private void maybeLogSlow(boolean success, int errorCode) {
        long thresholdMillis = runtime.config().slowRequestMillis();
        if (thresholdMillis <= 0) {
            return;
        }
        long durationNanos = System.nanoTime() - startNanos;
        long durationMillis = durationNanos / 1_000_000;
        if (durationMillis < thresholdMillis) {
            return;
        }
        log.warn(
                "slow request msgId={} seq={} playerId={} durationMs={} success={} errorCode={}",
                req.msgId(),
                req.seq(),
                state.playerId(),
                durationMillis,
                success,
                errorCode
        );
    }

    public record ResumeDecision(boolean resumeOk, long minBufferedPushId, long maxBufferedPushId, Runnable replay) {
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
        private final long seq;

        private DeferredReplyImpl(RequestContext ctx, long seq) {
            this.ctx = ctx;
            this.expectedEpoch = ctx.sessionEpoch();
            this.seq = seq;
        }

        @Override
        public boolean reply(Object resp) {
            if (!completed.compareAndSet(false, true)) {
                return false;
            }
            if (ctx.state.removeDeferred(seq) == null) {
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
            if (ctx.state.removeDeferred(seq) == null) {
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
            if (ctx.state.removeDeferred(seq) == null) {
                return false;
            }
            ctx.error(CivError.of(CivErrorCodes.SESSION_EXPIRED, "session expired", true));
            return true;
        }
    }
}
