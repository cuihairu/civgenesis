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
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteInvoker;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
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
    private final ConcurrentHashMap<Long, PlayerSession> playerSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Connection> activeConnectionsByPlayerId = new ConcurrentHashMap<>();

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
        states.put(connection.id(), new ConnectionState(connection.id(), config));
    }

    @Override
    public void onDisconnect(Connection connection) {
        ConnectionState state = states.remove(connection.id());
        if (state != null && state.playerId() != 0) {
            activeConnectionsByPlayerId.remove(state.playerId(), connection);
        }
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

        if (frame.ackPushId() > 0) {
            onAck(state, frame.ackPushId());
        }

        if (frame.type() == FrameType.PING) {
            connection.send(new Frame(FrameType.PONG, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            return;
        }
        if (frame.type() == FrameType.ACK) {
            if (frame.pushId() > 0) {
                onAck(state, frame.pushId());
            }
            return;
        }
        if (frame.type() == FrameType.PONG) {
            return;
        }
        if (frame.type() != FrameType.REQ) {
            return;
        }

        if (config.dedupEnabled() && frame.seq() > 0) {
            ResponseDedupCache.Entry cached = state.dedupCache().get(frame.seq(), System.currentTimeMillis());
            if (cached != null) {
                sendCachedResp(connection, cached);
                return;
            }
        }

        int msgId = frame.msgId();
        RouteInvoker invoker = routeTable.get(msgId);
        if (invoker == null) {
            sendError(connection, frame, CivError.of(CivErrorCodes.INVALID_FRAME, "unknown msgId", true), startNanos);
            return;
        }

        boolean requireLogin = !invoker.definition().open();
        boolean isBusiness = msgId >= 1000;
        if ((requireLogin || isBusiness) && state.playerId() == 0) {
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

    private void sendCachedResp(Connection connection, ResponseDedupCache.Entry cached) {
        ByteBuf payload;
        if (cached.payloadBytes().length == 0) {
            payload = Unpooled.EMPTY_BUFFER.retainedDuplicate();
        } else {
            payload = connection.alloc().buffer(cached.payloadBytes().length);
            payload.writeBytes(cached.payloadBytes());
        }
        connection.send(new Frame(
                FrameType.RESP,
                cached.msgId(),
                cached.seq(),
                0,
                cached.flags(),
                0,
                0,
                payload
        ));
    }

    void recordDedupResponse(ConnectionState state, long seq, int msgId, long flags, byte[] payloadBytes) {
        if (!config.dedupEnabled() || seq <= 0) {
            return;
        }
        state.dedupCache().put(seq, msgId, flags, payloadBytes, System.currentTimeMillis());
    }

    void attachPlayer(Connection connection, ConnectionState state, long playerId, boolean kickOld) {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId must be > 0");
        }
        Connection existing = activeConnectionsByPlayerId.put(playerId, connection);
        if (existing != null && existing.id() != connection.id()) {
            if (kickOld) {
                existing.close();
            } else {
                connection.close();
                throw new IllegalStateException("player already online");
            }
        }
        state.attachPlayer(playerId);
        PlayerSession session = playerSessions.computeIfAbsent(playerId,
                id -> new PlayerSession(id, config.maxBufferedPushCount(), config.maxBufferedPushAgeMillis()));
        state.playerSession(session);
    }

    RequestContext.ResumeDecision resume(Connection connection, ConnectionState state, long lastAppliedPushId) {
        PlayerSession session = state.playerSession();
        if (session == null) {
            return new RequestContext.ResumeDecision(false, 0, 0, () -> {});
        }
        PlayerSession.ReplayPlan plan = session.planReplay(lastAppliedPushId, System.currentTimeMillis());
        return new RequestContext.ResumeDecision(plan.ok(), plan.minBufferedPushId(), plan.maxBufferedPushId(), () -> {
            for (PlayerSession.PushEntry e : plan.toReplay()) {
                ByteBuf payload;
                if (e.payloadBytes().length == 0) {
                    payload = Unpooled.EMPTY_BUFFER.retainedDuplicate();
                } else {
                    payload = connection.alloc().buffer(e.payloadBytes().length);
                    payload.writeBytes(e.payloadBytes());
                }
                connection.send(new Frame(FrameType.PUSH, e.msgId(), 0, e.pushId(), e.flags(), e.tsMillis(), 0, payload));
            }
        });
    }

    long push(Connection connection, ConnectionState state, int msgId, long flags, byte[] payloadBytes) {
        PlayerSession session = state.playerSession();
        if (session == null) {
            throw new IllegalStateException("push requires attached player session");
        }
        long pushId = session.nextPushId();
        long ts = System.currentTimeMillis();
        session.recordPush(new PlayerSession.PushEntry(pushId, msgId, flags, ts, payloadBytes));

        ByteBuf payload;
        if (payloadBytes.length == 0) {
            payload = Unpooled.EMPTY_BUFFER.retainedDuplicate();
        } else {
            payload = connection.alloc().buffer(payloadBytes.length);
            payload.writeBytes(payloadBytes);
        }
        connection.send(new Frame(FrameType.PUSH, msgId, 0, pushId, flags, ts, 0, payload));
        return pushId;
    }

    private void onAck(ConnectionState state, long pushId) {
        PlayerSession session = state.playerSession();
        if (session != null) {
            session.ack(pushId);
        }
    }
}
