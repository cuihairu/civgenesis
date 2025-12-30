package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteInvoker;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DispatcherRuntime implements Dispatcher {
    private static final Logger log = LoggerFactory.getLogger(DispatcherRuntime.class);

    private final RouteTable routeTable;
    private final PayloadCodec codec;
    private final ShardExecutor shardExecutor;
    private final DispatcherConfig config;
    private final CivMetrics metrics;
    private final CivTracer tracer;

    private final ConcurrentHashMap<Long, ConnectionState> states = new ConcurrentHashMap<>();

    public DispatcherRuntime(RouteTable routeTable, PayloadCodec codec, ShardExecutor shardExecutor, DispatcherConfig config) {
        this(routeTable, codec, shardExecutor, config, CivMetrics.noop(), CivTracer.noop());
    }

    public DispatcherRuntime(
            RouteTable routeTable,
            PayloadCodec codec,
            ShardExecutor shardExecutor,
            DispatcherConfig config,
            CivMetrics metrics,
            CivTracer tracer
    ) {
        this.routeTable = Objects.requireNonNull(routeTable, "routeTable");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.shardExecutor = Objects.requireNonNull(shardExecutor, "shardExecutor");
        this.config = Objects.requireNonNullElse(config, DispatcherConfig.defaults());
        this.metrics = Objects.requireNonNullElse(metrics, CivMetrics.noop());
        this.tracer = Objects.requireNonNullElse(tracer, CivTracer.noop());
    }

    DispatcherConfig config() {
        return config;
    }

    @Override
    public void onConnect(Connection connection) {
        states.put(connection.id(), new ConnectionState(connection.id()));
    }

    @Override
    public void onDisconnect(Connection connection) {
        states.remove(connection.id());
    }

    @Override
    public void handle(Connection connection, Frame frame) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(frame, "frame");

        ConnectionState state = states.get(connection.id());
        if (state == null) {
            return;
        }
        long startNanos = System.nanoTime();

        if (frame.type() == FrameType.PING) {
            connection.send(new Frame(FrameType.PONG, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            return;
        }
        if (frame.type() == FrameType.ACK || frame.type() == FrameType.PONG) {
            return;
        }
        if (frame.type() != FrameType.REQ) {
            return;
        }

        int msgId = frame.msgId();
        RouteInvoker invoker = routeTable.get(msgId);
        if (invoker == null) {
            sendError(connection, frame, CivError.of(CivErrorCodes.INVALID_FRAME, "unknown msgId", true), startNanos);
            return;
        }

        boolean isBusiness = msgId >= 1000;
        if (isBusiness && state.playerId() == 0) {
            sendError(connection, frame, CivError.of(CivErrorCodes.NEED_LOGIN, "need login", true), startNanos);
            return;
        }

        if (!state.tryAddInFlight(frame.seq(), config.maxInFlightPerConnection())) {
            sendError(connection, frame, CivError.of(CivErrorCodes.BACKPRESSURE, "backpressure", true), startNanos);
            return;
        }
        if (frame.seq() > 0) {
            metrics.onInFlightInc();
        }

        long shardKey = switch (invoker.definition().shardBy()) {
            case PLAYER -> state.playerId() != 0 ? state.playerId() : connection.id();
            case CHANNEL -> connection.id();
        };

        if (frame.payload() != null) {
            frame.payload().retain();
        }

        shardExecutor.execute(shardKey, () -> {
            CivSpan span = tracer.startRequestSpan(connection.id(), state.playerId(), state.sessionEpoch(), msgId, frame.seq());
            RequestContext ctx = null;
            try (frame) {
                ctx = new RequestContext(connection, state, codec, frame, this, startNanos, span);
                try {
                    invoker.invoke(ctx, frame);
                    if (!ctx.hasResponded() && !ctx.isDeferred()) {
                        ctx.error(CivError.of(CivErrorCodes.SERVER_BUSY, "no response", true));
                    }
                } finally {
                    ctx.close();
                }
            } catch (Throwable t) {
                span.recordException(t);
                span.setError("dispatch error");
                log.error("dispatch error msgId={}", msgId, t);
                try {
                    if (ctx == null || !ctx.hasResponded()) {
                        sendError(connection, frame, CivError.of(CivErrorCodes.SERVER_BUSY, "server error", true), startNanos);
                    }
                } catch (Exception ignore) {
                } finally {
                    onResponseComplete(state, frame.seq());
                    if (frame.payload() != null && frame.payload().refCnt() > 0) {
                        frame.payload().release();
                    }
                }
                if (ctx == null || !ctx.hasResponded()) {
                    span.close();
                }
            }
        });
    }

    void onResponseComplete(ConnectionState state, long seq) {
        boolean removed = state.removeInFlight(seq);
        if (removed) {
            metrics.onInFlightDec();
        }
    }

    void onRequestComplete(int msgId, boolean success, int errorCode, long durationNanos) {
        metrics.onRequestComplete(msgId, success, errorCode, durationNanos);
    }

    private void sendError(Connection connection, Frame req, CivError error, long startNanos) {
        ConnectionState state = states.get(connection.id());
        if (state == null) {
            return;
        }
        CivSpan span = tracer.startRequestSpan(connection.id(), state.playerId(), state.sessionEpoch(), req.msgId(), req.seq());
        try (RequestContext ctx = new RequestContext(connection, state, codec, req, this, startNanos, span)) {
            ctx.error(error);
        }
    }
}
