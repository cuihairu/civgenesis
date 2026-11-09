package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatcherRuntimeEdgeTest {

    private static DispatcherConfig config(int maxInFlight, int perShard, long requestTimeout) {
        return new DispatcherConfig(maxInFlight, perShard, RawPayloadMode.RETAIN, false,
                requestTimeout, 0, true, 8, 30_000, 4, 60_000);
    }

    private static Frame req(int msgId, long seq, long ackPushId) {
        return new Frame(FrameType.REQ, msgId, seq, 0, 0, 0, ackPushId,
                Unpooled.buffer().writeBytes(new byte[]{1, 2, 3}));
    }

    private static int errorCode(TestConnection.CapturedFrame frame) {
        assertTrue((frame.flags() & ProtocolFlags.ERROR) != 0);
        return respCode(frame);
    }

    private static int respCode(TestConnection.CapturedFrame frame) {
        try {
            return Error.parseFrom(frame.payload()).getCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void disconnectClearsInFlightAndClosesDeferredSpans() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            assertTrue(state.tryAddInFlight(1, 8, 0));
            assertTrue(state.tryAddInFlight(2, 8, 0));
            // a span whose setError throws still gets closed through the finally branch
            final boolean[] closed = {false};
            CivSpan failingSpan = new CivSpan() {
                @Override
                public void setError(String message) {
                    throw new IllegalStateException("span failed");
                }

                @Override
                public void close() {
                    closed[0] = true;
                }
            };
            ConnectionState.DeferredEntry deferred = new ConnectionState.DeferredEntry(5, 7, 1, 0, 0, failingSpan);
            assertTrue(state.registerDeferred(deferred));

            runtime.onDisconnect(conn);
            assertEquals(0, state.inFlightCount());
            assertTrue(closed[0]);
        }
    }

    @Test
    void ackPushIdOnRequestIsProcessed() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new ReplyController())), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);

            // ackPushId > 0 routes into onAck (no session yet -> no-op) and the frame still dispatches
            runtime.handle(conn, req(80, 1, 2));
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 1);
            assertEquals(0, respCode(conn.frames.get(0))); // ReplyController replies with code 0
        }
    }

    @Test
    void businessAndProtectedRoutesRequireLogin() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new BusinessController(), new ProtectedController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);

            // business route id >= 1000 needs login even when open
            runtime.handle(conn, req(1000, 1, 0));
            // protected route (open = false) needs login
            runtime.handle(conn, req(50, 2, 0));
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 2);
            assertEquals(CivErrorCodes.NEED_LOGIN, errorCode(conn.frames.get(0)));
            assertEquals(CivErrorCodes.NEED_LOGIN, errorCode(conn.frames.get(1)));
        }
    }

    @Test
    void channelShardUsesConnectionId() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new ChannelController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(7);
            runtime.onConnect(conn);

            runtime.handle(conn, req(60, 1, 0));
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 1);
            assertEquals(0, respCode(conn.frames.get(0)));
        }
    }

    @Test
    void overLimitInFlightReturnsBackpressure() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            // maxInFlightPerConnection = 1 while the shard limit is generous, so the second request
            // fails the per-connection duplicate/over-limit check rather than the shard check.
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new DeferController())),
                    new ProtobufPayloadCodec(), shards, config(1, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);

            runtime.handle(conn, req(31, 1, 0)); // first request stays in-flight (deferred)
            runtime.handle(conn, req(31, 2, 0)); // second exceeds the per-connection limit
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.stream().anyMatch(f -> {
                try {
                    return errorCode(f) == CivErrorCodes.BACKPRESSURE;
                } catch (AssertionError e) {
                    return false;
                }
            }));
            runtime.onDisconnect(conn);
        }
    }

    @Test
    void dispatchErrorSwallowsSendFailure() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            RecordingTracer tracer = new RecordingTracer();
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new ThrowController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000),
                    io.github.cuihairu.civgenesis.core.observability.CivMetrics.noop(), tracer);
            Connection bad = new ThrowingConnection(1);
            runtime.onConnect(bad);

            runtime.handle(bad, req(70, 1, 0));
            await().atMost(Duration.ofSeconds(10)).until(() -> tracer.closed > 0);
            assertTrue(tracer.exceptions > 0);
        }
    }

    @Test
    void dispatchErrorToleratesDisconnectedState() throws Exception {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            RecordingTracer tracer = new RecordingTracer();
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new BlockThrowController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000),
                    io.github.cuihairu.civgenesis.core.observability.CivMetrics.noop(), tracer);
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);

            runtime.handle(conn, req(71, 1, 0));
            assertTrue(BlockThrowController.entered.await(5, TimeUnit.SECONDS));
            runtime.onDisconnect(conn); // removes the state before the handler finishes
            BlockThrowController.release.countDown();

            await().atMost(Duration.ofSeconds(10)).until(() -> tracer.closed > 0);
            assertTrue(tracer.exceptions > 0);
        }
    }

    @Test
    void cancelDeferredClosesSpanAndToleratesMissing() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            runtime.cancelDeferred(state, 5); // no entry -> no-op
            ConnectionState.DeferredEntry entry = new ConnectionState.DeferredEntry(5, 7, 1, 0, 0, CivSpan.noop());
            assertTrue(state.registerDeferred(entry));
            runtime.cancelDeferred(state, 5);
        }
    }

    @Test
    void timeoutHandlesDisconnectedAndEpochMismatch() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            // disconnected: the timeout fires but the state is gone
            ConnectionState.DeferredEntry gone = new ConnectionState.DeferredEntry(6, 7, 1, 0, 0, CivSpan.noop());
            assertTrue(state.registerDeferred(gone));
            runtime.onDeferredTimeout(new TestConnection(99), state, gone, System.nanoTime());

            // session expired: the epoch advanced after the deferred reply was registered
            state.attachPlayer(9);
            ConnectionState.DeferredEntry expired = new ConnectionState.DeferredEntry(7, 7, 1, 0, 0, CivSpan.noop());
            assertTrue(state.registerDeferred(expired));
            runtime.onDeferredTimeout(conn, state, expired, System.nanoTime());
        }
    }

    @Test
    void timeoutForAlreadyRemovedEntryReturnsEarly() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            // the deferred entry is not registered (already consumed) -> the timeout returns early
            ConnectionState.DeferredEntry ghost = new ConnectionState.DeferredEntry(10, 7, 1, 0, 0, CivSpan.noop());
            runtime.onDeferredTimeout(conn, state, ghost, System.nanoTime());
        }
    }

    @Test
    void timeoutWithNullErrorPayloadSendsEmpty() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new NullErrorCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);
            state.attachPlayer(9);

            ConnectionState.DeferredEntry entry = new ConnectionState.DeferredEntry(9, 7, 1, state.sessionEpoch(), 0, CivSpan.noop());
            assertTrue(state.registerDeferred(entry));
            runtime.onDeferredTimeout(conn, state, entry, System.nanoTime());
            assertEquals(1, conn.frames.size());
            assertEquals(0, conn.frames.get(0).payload().length);
        }
    }

    @Test
    void echoRouteRepliesWithReturnValue() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new EchoController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);

            // non-void, non-raw handler: the invoker decodes, invokes, and replies with the return value
            Frame frame = new Frame(FrameType.REQ, 90, 1, 0, 0, 0, 0,
                    Unpooled.wrappedBuffer(Error.newBuilder().setCode(0).setMessage("req").build().toByteArray()));
            runtime.handle(conn, frame);
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 1);
            assertEquals(0, respCode(conn.frames.get(0)));
        }
    }

    @Test
    void timeoutSuccessSendsRequestTimeoutAndClosesSpan() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);
            state.attachPlayer(9);

            ConnectionState.DeferredEntry entry = new ConnectionState.DeferredEntry(9, 7, 1, state.sessionEpoch(), 0, CivSpan.noop());
            assertTrue(state.registerDeferred(entry));
            runtime.onDeferredTimeout(conn, state, entry, System.nanoTime());

            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 1);
            assertEquals(CivErrorCodes.REQUEST_TIMEOUT, errorCode(conn.frames.get(0)));
        }
    }

    @Test
    void timeoutSendFailureClosesSpan() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            Connection bad = new ThrowingConnection(1);
            runtime.onConnect(bad);
            ConnectionState state = runtime.stateForTest(1);
            state.attachPlayer(9);

            ConnectionState.DeferredEntry entry = new ConnectionState.DeferredEntry(9, 7, 1, state.sessionEpoch(), 0, CivSpan.noop());
            assertTrue(state.registerDeferred(entry));
            // send fails inside onDeferredTimeout -> recordException + setError + close
            runtime.onDeferredTimeout(bad, state, entry, System.nanoTime());
        }
    }

    @Test
    void cachedResponseWithEmptyPayloadSendsEmptyBuffer() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    new RouteScanner().scan(List.of(new ReplyController())),
                    new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            state.dedupCache().put(3, 80, 0, new byte[0], System.currentTimeMillis());
            runtime.handle(conn, req(80, 3, 0));
            await().atMost(Duration.ofSeconds(10)).until(() -> conn.frames.size() == 1);
            assertEquals(3, conn.frames.get(0).seq());
            assertEquals(0, conn.frames.get(0).payload().length);
        }
    }

    @Test
    void resumeWithoutSessionReturnsNoReplay() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            RequestContext.ResumeDecision decision = runtime.resume(conn, state, 0);
            assertFalse(decision.resumeOk());
            assertEquals(0, decision.minBufferedPushId());
            assertEquals(0, decision.maxBufferedPushId());
            decision.replay().run(); // the no-op replay still executes its (empty) body
        }
    }

    @Test
    void replayEmptyPushPayload() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            runtime.attachPlayer(conn, state, 9, false);
            state.playerSession().recordPush(new PlayerSession.PushEntry(1, 7, 0, System.currentTimeMillis(), new byte[0]));

            RequestContext.ResumeDecision decision = runtime.resume(conn, state, 0);
            assertTrue(decision.resumeOk());
            decision.replay().run();
            assertEquals(1, conn.frames.size());
            assertEquals(FrameType.PUSH, conn.frames.get(0).type());
            assertEquals(0, conn.frames.get(0).payload().length);
        }
    }

    @Test
    void pushEmptyPayload() {
        try (ShardExecutor shards = new ShardExecutor(1, "edge")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(8, 8, 60_000));
            TestConnection conn = new TestConnection(1);
            runtime.onConnect(conn);
            ConnectionState state = runtime.stateForTest(1);

            runtime.attachPlayer(conn, state, 9, false);
            long pushId = runtime.pushForTest(conn, state, 7, 0, new byte[0]);
            assertTrue(pushId > 0);
            assertEquals(FrameType.PUSH, conn.frames.get(0).type());
            assertEquals(0, conn.frames.get(0).payload().length);
        }
    }

    @GameController
    public static final class ReplyController {
        @GameRoute(id = 80, open = true)
        public void reply(RequestContext ctx, ByteBuf payload) {
            ctx.reply(Error.newBuilder().setCode(0).build());
        }
    }

    @GameController
    public static final class EchoController {
        @GameRoute(id = 90, open = true)
        public Error echo(RequestContext ctx, Error req) {
            return req;
        }
    }

    @GameController
    public static final class BusinessController {
        @GameRoute(id = 1000, open = true)
        public void business(RequestContext ctx, ByteBuf payload) {
            ctx.reply(Error.getDefaultInstance());
        }
    }

    @GameController
    public static final class ProtectedController {
        @GameRoute(id = 50, open = false)
        public void guarded(RequestContext ctx, ByteBuf payload) {
            ctx.reply(Error.getDefaultInstance());
        }
    }

    @GameController
    public static final class ChannelController {
        @GameRoute(id = 60, open = true, shardBy = io.github.cuihairu.civgenesis.dispatcher.route.ShardBy.CHANNEL)
        public void channel(RequestContext ctx, ByteBuf payload) {
            ctx.reply(Error.newBuilder().setCode(0).build());
        }
    }

    @GameController
    public static final class DeferController {
        @GameRoute(id = 31, open = true)
        public void defer(RequestContext ctx, ByteBuf payload) {
            ctx.defer();
        }
    }

    @GameController
    public static final class ThrowController {
        @GameRoute(id = 70, open = true)
        public void fail(RequestContext ctx, ByteBuf payload) {
            throw new IllegalStateException("handler failed");
        }
    }

    @GameController
    public static final class BlockThrowController {
        static final CountDownLatch entered = new CountDownLatch(1);
        static final CountDownLatch release = new CountDownLatch(1);

        @GameRoute(id = 71, open = true)
        public void blockThenFail(RequestContext ctx, ByteBuf payload) throws Exception {
            entered.countDown();
            release.await();
            throw new IllegalStateException("handler failed after disconnect");
        }
    }

    private static final class NullErrorCodec implements io.github.cuihairu.civgenesis.core.codec.PayloadCodec {
        private final ProtobufPayloadCodec delegate = new ProtobufPayloadCodec();

        @Override
        public <T> T decode(int msgId, Class<T> targetType, ByteBuf payload) throws Exception {
            return delegate.decode(msgId, targetType, payload);
        }

        @Override
        public ByteBuf encode(ByteBufAllocator alloc, Object message) throws Exception {
            return delegate.encode(alloc, message);
        }

        @Override
        public ByteBuf encodeError(ByteBufAllocator alloc, io.github.cuihairu.civgenesis.core.error.CivError error) {
            return null;
        }
    }

    private static final class ThrowingConnection implements Connection {
        private final long id;

        private ThrowingConnection(long id) {
            this.id = id;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ByteBufAllocator alloc() {
            return UnpooledByteBufAllocator.DEFAULT;
        }

        @Override
        public void send(Frame frame) {
            frame.close();
            throw new IllegalStateException("send failed");
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingTracer implements CivTracer {
        int exceptions;
        int errors;
        int closed;

        public CivSpan startRequestSpan(long connectionId, long playerId, long sessionEpoch, int msgId, long seq) {
            return recordingSpan();
        }

        @Override
        public CivSpan startJobSpan(String jobName, long runId) {
            return recordingSpan();
        }

        private CivSpan recordingSpan() {
            return new CivSpan() {
                @Override
                public void recordException(Throwable ignored) {
                    exceptions++;
                }

                @Override
                public void setError(String ignored) {
                    errors++;
                }

                @Override
                public void close() {
                    closed++;
                }
            };
        }
    }
}
