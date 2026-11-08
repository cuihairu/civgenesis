package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

class DispatcherRuntimePathsTest {
    private static DispatcherConfig config(boolean dedup, int perShard) {
        return new DispatcherConfig(2, perShard, RawPayloadMode.RETAIN, true, 50, 0,
                dedup, 8, 30_000, 4, 60_000);
    }

    @Test
    void ignoresUnknownConnectionAndControlFrames() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, config(true, 1));
            TestConnection connected = new TestConnection(1);
            runtime.onConnect(connected);
            assertDoesNotThrow(() -> runtime.handle(new TestConnection(9), request(10, 1, false)));
            runtime.handle(connected, new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            runtime.handle(connected, new Frame(FrameType.ACK, 0, 0, 2, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            runtime.handle(connected, new Frame(FrameType.PONG, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            runtime.handle(connected, new Frame(FrameType.RESP, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            runtime.onDisconnect(connected);
            assertEquals(FrameType.PONG, connected.frames.get(0).type());
        }
    }

    @Test
    void rejectsCompressionUnknownRouteAndLogin() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new RawController())), new ProtobufPayloadCodec(), shards, config(false, 1));
            TestConnection connection = new TestConnection(1);
            runtime.onConnect(connection);

            Frame compressed = new Frame(FrameType.REQ, 5, 1, 0, ProtocolFlags.COMPRESS, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
            runtime.handle(connection, compressed);
            assertEquals(CivErrorCodes.UNSUPPORTED_PROTOCOL, error(connection.frames.get(0)));

            Frame unknown = new Frame(FrameType.REQ, 999, 2, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
            runtime.handle(connection, unknown);
            assertEquals(CivErrorCodes.INVALID_FRAME, error(connection.frames.get(1)));

            Frame protectedRoute = new Frame(FrameType.REQ, 6, 3, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
            runtime.handle(connection, protectedRoute);
            assertEquals(CivErrorCodes.INVALID_FRAME, error(connection.frames.get(2)));
        }
    }

    @Test
    void attachesKicksAndRejectsDuplicatePlayers() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new LoginController())), new ProtobufPayloadCodec(), shards,
                    new DispatcherConfig(8, 0, RawPayloadMode.RETAIN, false, 50, 0, false, 8, 30_000, 4, 60_000));
            TestConnection first = new TestConnection(1);
            TestConnection second = new TestConnection(2);
            runtime.onConnect(first);
            runtime.onConnect(second);

            runtime.handle(first, new Frame(FrameType.REQ, 11, 1, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> runtime.stateForTest(first.id).playerId() == 1);
            assertTrue(first.isActive());

            runtime.handle(second, new Frame(FrameType.REQ, 11, 1, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> !first.isActive());
            assertTrue(second.isActive());

            TestConnection third = new TestConnection(3);
            runtime.onConnect(third);
            runtime.handle(third, new Frame(FrameType.REQ, 12, 2, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> !third.isActive());
            assertTrue(second.isActive());

            assertThrows(IllegalStateException.class, () -> runtime.attachPlayer(second, runtime.stateForTest(second.id), 1, false));
            assertThrows(IllegalArgumentException.class, () -> runtime.attachPlayer(first, runtime.stateForTest(first.id), 0, true));
        }
    }

    @Test
    void cachesDuplicateResponsesAndHandlesBackpressure() throws Exception {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new SlowController())), new ProtobufPayloadCodec(), shards, config(true, 1));
            TestConnection first = new TestConnection(1);
            TestConnection second = new TestConnection(2);
            runtime.onConnect(first);
            runtime.onConnect(second);

            runtime.handle(first, new Frame(FrameType.REQ, 20, 1, 0, 0, 0, 0, null));
            await().atMost(Duration.ofSeconds(10)).until(() -> first.frames.size() == 1);
            runtime.handle(first, new Frame(FrameType.REQ, 20, 1, 0, 0, 0, 0, null));
            await().atMost(Duration.ofSeconds(10)).until(() -> first.frames.size() == 2);
            assertEquals(
                    java.util.Arrays.toString(first.frames.get(0).payload()),
                    java.util.Arrays.toString(first.frames.get(1).payload()));

            runtime.handle(second, new Frame(FrameType.REQ, 20, 2, 0, 0, 0, 0, null));
            runtime.handle(second, new Frame(FrameType.REQ, 20, 3, 0, 0, 0, 0, null));
            await().atMost(Duration.ofSeconds(10)).until(() -> second.frames.stream().anyMatch(f -> error(f) == CivErrorCodes.BACKPRESSURE));
        }
    }

    @Test
    void catchesHandlerFailureAndTimeoutSendsErrors() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            RecordingTracer tracer = new RecordingTracer();
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new FailingController(), new DeferController())), new ProtobufPayloadCodec(), shards, config(false, 0), io.github.cuihairu.civgenesis.core.observability.CivMetrics.noop(), tracer);
            TestConnection connection = new TestConnection(1);
            runtime.onConnect(connection);

            runtime.handle(connection, new Frame(FrameType.REQ, 30, 1, 0, 0, 0, 0, errorPayload(0)));
            runtime.handle(connection, new Frame(FrameType.REQ, 31, 2, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 2);
            assertEquals(CivErrorCodes.SERVER_BUSY, error(connection.frames.get(0)));
            assertEquals(CivErrorCodes.REQUEST_TIMEOUT, error(connection.frames.get(1)));
            assertTrue(tracer.exceptions > 0);
            assertTrue(tracer.errors > 0);
            assertTrue(tracer.closed > 0);
        }
    }

    @Test
    void pushesReplaysAndAcksThroughResume() throws Exception {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new SessionController())), new ProtobufPayloadCodec(), shards,
                    new DispatcherConfig(8, 0, RawPayloadMode.RETAIN, false, 50, 0, false, 8, 30_000, 4, 60_000));
            TestConnection connection = new TestConnection(1);
            runtime.onConnect(connection);

            runtime.handle(connection, new Frame(FrameType.REQ, 40, 1, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 3);
            await().atMost(Duration.ofSeconds(10)).until(() -> runtime.stateForTest(connection.id).playerSession() != null);
            assertEquals(FrameType.PUSH, connection.frames.get(0).type());
            assertEquals(1, connection.frames.get(0).pushId());
            assertEquals(FrameType.PUSH, connection.frames.get(1).type());
            assertEquals(2, connection.frames.get(1).pushId());

            long manual = runtime.pushForTest(connection, runtime.stateForTest(connection.id), 102, 0, new byte[] {9});
            assertEquals(3, manual);
            assertEquals(FrameType.PUSH, connection.frames.get(3).type());
            assertEquals(3, connection.frames.get(3).pushId());

            runtime.handle(connection, new Frame(FrameType.ACK, 0, 0, 2, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            runtime.handle(connection, new Frame(FrameType.REQ, 41, 2, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 6);
            assertEquals(FrameType.PUSH, connection.frames.get(4).type());
            assertEquals(3, connection.frames.get(4).pushId());
            assertEquals(FrameType.RESP, connection.frames.get(5).type());
            runtime.onDisconnect(connection);
        }
    }

    @Test
    void deferredLifecycleHandlesDuplicatesAndExpiredSession() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of(new DeferredLifecycleController())), new ProtobufPayloadCodec(), shards,
                    new DispatcherConfig(8, 0, RawPayloadMode.RETAIN, false, 60_000, 0, false, 8, 30_000, 4, 60_000));
            TestConnection connection = new TestConnection(1);
            runtime.onConnect(connection);
            runtime.handle(connection, new Frame(FrameType.REQ, 50, 7, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> DeferredLifecycleController.lastReply != null);
            DeferredReply reply = DeferredLifecycleController.lastReply;
            assertThrows(IllegalStateException.class, () -> DeferredLifecycleController.lastContext.defer());
            assertTrue(reply.cancel());
            assertFalse(reply.cancel());

            runtime.handle(connection, new Frame(FrameType.REQ, 50, 8, 0, 0, 0, 0, errorPayload(0)));
            await().atMost(Duration.ofSeconds(10)).until(() -> DeferredLifecycleController.lastReply != null && !DeferredLifecycleController.lastReply.equals(reply));
            runtime.attachPlayer(connection, runtime.stateForTest(connection.id), 9, true);
            assertFalse(DeferredLifecycleController.lastReply.reply("late"));
        }
    }

    @Test
    void validateCoreArgumentsAndConfig() {
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()), new ProtobufPayloadCodec(), shards, null);
            assertThrows(NullPointerException.class, () -> runtime.handle(null, request(1, 1, false)));
            assertEquals(DispatcherConfig.defaults(), runtime.config());
            assertEquals(64, DispatcherConfig.defaults().maxInFlightPerConnection());
            assertEquals(2048, DispatcherConfig.defaults().maxInFlightPerShard());
            assertEquals(RawPayloadMode.RETAIN, DispatcherConfig.defaults().rawPayloadMode());
            assertTrue(DispatcherConfig.defaults().dedupEnabled());
        }
    }

    private static Frame request(int msgId, long seq, boolean payload) {
        return new Frame(FrameType.REQ, msgId, seq, 0, 0, 0, 0,
                payload ? Unpooled.wrappedBuffer(new byte[] {1}).retainedDuplicate() : null);
    }

    private static ByteBuf errorPayload(int code) {
        return Unpooled.wrappedBuffer(Error.newBuilder().setCode(code).setMessage("req").build().toByteArray());
    }

    private static int error(TestConnection.CapturedFrame frame) {
        assertTrue((frame.flags() & ProtocolFlags.ERROR) != 0);
        try {
            Error decoded = Error.parseFrom(frame.payload());
            return decoded.getCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @GameController
    public static final class RawController {
        @GameRoute(id = 5, open = true)
        public void raw(RequestContext ctx, io.netty.buffer.ByteBuf payload) {
            ctx.reply(io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().build());
        }
    }

    @GameController
    public static final class LoginController {
        @GameRoute(id = 11, open = true)
        public void kick(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            ctx.attachPlayer(1, true);
        }

        @GameRoute(id = 12, open = true)
        public void reject(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            ctx.attachPlayer(1, false);
        }
    }

    @GameController
    public static final class SlowController {
        @GameRoute(id = 20, open = true)
        public void slow(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) throws Exception {
            Thread.sleep(100);
            ctx.reply(io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().setCode(7).build());
        }
    }

    @GameController
    public static final class FailingController {
        @GameRoute(id = 30, open = true)
        public void fail(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            throw new IllegalStateException("handler failed");
        }
    }

    @GameController
    public static final class DeferController {
        @GameRoute(id = 31, open = true)
        public void defer(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            ctx.defer();
        }
    }

    @GameController
    public static final class SessionController {
        @GameRoute(id = 40, open = true)
        public void attach(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            ctx.attachPlayer(1, true);
            ctx.push(100, io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().setCode(1).build(), ProtocolFlags.ACK_REQUIRED);
            ctx.push(101, io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().setCode(2).build(), ProtocolFlags.ACK_REQUIRED);
            ctx.reply(io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().setCode(0).build());
        }

        @GameRoute(id = 41, open = true)
        public void resume(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            RequestContext.ResumeDecision decision = ctx.resume(2);
            assertTrue(decision.resumeOk());
            assertEquals(3, decision.maxBufferedPushId());
            decision.replay().run();
            ctx.reply(io.github.cuihairu.civgenesis.protocol.system.Error.newBuilder().setCode(0).build());
        }
    }

    @GameController
    public static final class DeferredLifecycleController {
        static volatile RequestContext lastContext;
        static volatile DeferredReply lastReply;

        @GameRoute(id = 50, open = true)
        public void defer(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            DeferredReply thisReply = ctx.defer();
            lastContext = ctx;
            lastReply = thisReply;
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
