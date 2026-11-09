package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.protocol.Compression;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestContextEdgeTest {
    private static final int MSG = 7;
    private ShardExecutor shards;
    private DispatcherRuntime runtime;
    private ConnectionState state;
    private TestConnection conn;

    @BeforeEach
    void setUp() {
        shards = new ShardExecutor(1, "ctx-test");
        runtime = new DispatcherRuntime(new RouteScanner().scan(List.of()),
                new ProtobufPayloadCodec(), shards, config(5_000, 0, RawPayloadMode.RETAIN));
        conn = new TestConnection(1);
        runtime.onConnect(conn);
        state = runtime.stateForTest(1);
    }

    @AfterEach
    void tearDown() {
        shards.close();
    }

    private static DispatcherConfig config(long requestTimeout, long slowRequestMillis, RawPayloadMode mode) {
        return new DispatcherConfig(2, 10, mode, false, requestTimeout, slowRequestMillis,
                true, 8, 30_000, 4, 60_000);
    }

    private Frame request(long seq) {
        return new Frame(FrameType.REQ, MSG, seq, 0, 0, 0, 0,
                Unpooled.buffer().writeBytes(new byte[]{1, 2, 3}));
    }

    private RequestContext ctx(Frame req) {
        return new RequestContext(conn, state, new ProtobufPayloadCodec(), req, runtime,
                System.nanoTime(), null, 1);
    }

    @Test
    void exposesAccessorsAndToleratesNullSpan() throws Exception {
        Frame req = request(1);
        RequestContext context = ctx(req);
        try (context) {
            assertEquals(conn.id(), context.connectionId());
            assertEquals(0, context.playerId());
            assertEquals(MSG, context.msgId());
            assertEquals(1, context.seq());
            assertEquals(state.sessionEpoch(), context.sessionEpoch());
        } finally {
            req.close();
        }
    }

    @Test
    void deferRejectsRespondedAndZeroSeq() throws Exception {
        Frame req = request(1);
        RequestContext responded = ctx(req);
        try {
            responded.reply(Error.getDefaultInstance());
            assertThrows(IllegalStateException.class, responded::defer);
        } finally {
            req.close();
        }

        Frame zeroSeq = new Frame(FrameType.REQ, MSG, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
        try {
            RequestContext zero = ctx(zeroSeq);
            assertThrows(IllegalStateException.class, zero::defer);
        } finally {
            zeroSeq.close();
        }
    }

    @Test
    void decodesCompressedPayloadOnlyWhenNegotiated() throws Exception {
        Error message = Error.newBuilder().setCode(5).build();
        ByteBuf encoded = new ProtobufPayloadCodec().encode(conn.alloc(), message);
        ByteBuf gz = GzipPayloads.compress(conn.alloc(), encoded);
        encoded.release();

        state.compression(Compression.GZIP);
        Frame compressed = new Frame(FrameType.REQ, MSG, 1, 0, ProtocolFlags.COMPRESS, 0, 0, gz.retainedDuplicate());
        try {
            RequestContext context = ctx(compressed);
            assertEquals(5, context.decode(Error.class, compressed).getCode());
        } finally {
            compressed.close();
        }

        state.compression(Compression.NONE);
        Frame unnegotiated = new Frame(FrameType.REQ, MSG, 1, 0, ProtocolFlags.COMPRESS, 0, 0, gz.retainedDuplicate());
        try {
            RequestContext context = ctx(unnegotiated);
            assertThrows(IllegalStateException.class, () -> context.decode(Error.class, unnegotiated));
        } finally {
            unnegotiated.close();
            gz.release();
        }
    }

    @Test
    void rawPayloadHandlesEmptyCopyAndRetainModes() throws Exception {
        Frame empty = new Frame(FrameType.REQ, MSG, 1, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
        try {
            RequestContext context = ctx(empty);
            assertEquals(0, context.rawPayload(empty).readableBytes());
        } finally {
            empty.close();
        }

        Frame req = request(1);
        try {
            RequestContext context = ctx(req);
            assertSamePayload(req, context.rawPayload(req));
        } finally {
            req.close();
        }

        // COPY mode: rawPayload returns an owned copy that close() releases
        DispatcherRuntime copyRuntime = new DispatcherRuntime(new RouteScanner().scan(List.of()),
                new ProtobufPayloadCodec(), shards, config(5_000, 0, RawPayloadMode.COPY));
        copyRuntime.onConnect(conn);
        ConnectionState copyState = copyRuntime.stateForTest(conn.id());
        Frame copyReq = request(1);
        try {
            RequestContext context = new RequestContext(conn, copyState, new ProtobufPayloadCodec(),
                    copyReq, copyRuntime, System.nanoTime(), null, 1);
            ByteBuf copied = context.rawPayload(copyReq);
            assertTrue(copied.isReadable());
            context.close();
        } finally {
            copyReq.close();
        }
    }

    @Test
    void replyAndErrorRejectDuplicateResponses() throws Exception {
        Frame req = request(1);
        try {
            RequestContext context = ctx(req);
            context.reply(Error.getDefaultInstance());
            assertThrows(IllegalStateException.class, () -> context.reply(Error.getDefaultInstance()));
        } finally {
            req.close();
        }

        Frame errReq = request(2);
        try {
            RequestContext context = ctx(errReq);
            context.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true));
            assertThrows(IllegalStateException.class,
                    () -> context.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "again", true)));
        } finally {
            errReq.close();
        }
    }

    @Test
    void replyAndErrorCompressWhenNegotiated() throws Exception {
        state.compression(Compression.GZIP);
        Frame req = request(1);
        try {
            RequestContext context = ctx(req);
            context.reply(Error.newBuilder().setCode(3).build());
            assertTrue(conn.frames.get(0).flags() != 0);
            assertEquals(1, conn.frames.get(0).seq());
        } finally {
            req.close();
        }

        Frame errReq = request(2);
        try {
            RequestContext context = ctx(errReq);
            context.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true));
            assertTrue((conn.frames.get(1).flags() & ProtocolFlags.ERROR) != 0);
        } finally {
            errReq.close();
        }
    }

    @Test
    void replyAndErrorPropagateSendFailures() throws Exception {
        Connection failing = new ThrowingConnection(9);
        runtime.onConnect(failing);
        ConnectionState failingState = runtime.stateForTest(9);
        Frame req = request(1);
        try {
            RequestContext context = new RequestContext(failing, failingState, new ProtobufPayloadCodec(),
                    req, runtime, System.nanoTime(), null, 1);
            assertThrows(RuntimeException.class, () -> context.reply(Error.getDefaultInstance()));
        } finally {
            req.close();
        }

        Frame errReq = request(2);
        try {
            RequestContext context = new RequestContext(failing, failingState, new ProtobufPayloadCodec(),
                    errReq, runtime, System.nanoTime(), null, 1);
            assertThrows(RuntimeException.class,
                    () -> context.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true)));
        } finally {
            errReq.close();
        }
    }

    @Test
    void pushRequiresSessionAndCompressesWhenNegotiated() throws Exception {
        Frame req = request(1);
        try {
            RequestContext noSession = ctx(req);
            // the context wraps the runtime's IllegalStateException in a RuntimeException
            assertThrows(RuntimeException.class,
                    () -> noSession.push(MSG, Error.getDefaultInstance(), 0));
            // the runtime itself reports the missing session directly
            assertThrows(IllegalStateException.class,
                    () -> runtime.pushForTest(conn, state, MSG, 0, new byte[]{1}));
        } finally {
            req.close();
        }

        runtime.attachPlayer(conn, state, 5, false);
        state.compression(Compression.GZIP);
        Frame pushReq = request(2);
        try {
            RequestContext context = ctx(pushReq);
            long pushId = context.push(MSG, Error.newBuilder().setCode(1).build(), 0);
            assertTrue(pushId > 0);
            assertEquals(FrameType.PUSH, conn.frames.get(0).type());
            assertThrows(RuntimeException.class, () -> context.push(MSG, "not-a-message", 0));
        } finally {
            pushReq.close();
        }
    }

    @Test
    void setCompressionAndSlowLogging() throws Exception {
        DispatcherRuntime slowRuntime = new DispatcherRuntime(new RouteScanner().scan(List.of()),
                new ProtobufPayloadCodec(), shards, config(5_000, 1, RawPayloadMode.RETAIN));
        slowRuntime.onConnect(conn);
        ConnectionState slowState = slowRuntime.stateForTest(conn.id());
        Frame req = request(1);
        try {
            RequestContext context = new RequestContext(conn, slowState, new ProtobufPayloadCodec(),
                    req, slowRuntime, System.nanoTime() - 5_000_000, null, 1);
            context.setCompression(Compression.GZIP);
            assertEquals(Compression.GZIP, slowState.compression());
            context.reply(Error.getDefaultInstance()); // duration > 1ms triggers slow log
        } finally {
            req.close();
        }
    }

    @Test
    void deferredReplyLifecycle() throws Exception {
        Frame req = request(1);
        try {
            RequestContext context = ctx(req);
            DeferredReply deferred = context.defer();
            assertTrue(context.isDeferred());
            state.removeDeferred(1); // simulate the entry already consumed
            assertFalse(deferred.reply(Error.getDefaultInstance()));

            // success path
            Frame okReq = request(2);
            try {
                RequestContext ok = ctx(okReq);
                DeferredReply okReply = ok.defer();
                assertTrue(okReply.reply(Error.getDefaultInstance()));
                assertFalse(okReply.reply(Error.getDefaultInstance())); // already completed
            } finally {
                okReq.close();
            }

            // epoch mismatch
            Frame epochReq = request(3);
            try {
                RequestContext epoch = ctx(epochReq);
                DeferredReply epochReply = epoch.defer();
                state.detachPlayer();
                assertFalse(epochReply.reply(Error.getDefaultInstance()));
            } finally {
                epochReq.close();
            }
        } finally {
            req.close();
        }
    }

    @Test
    void deferredErrorAndCancelLifecycle() throws Exception {
        Frame errReq = request(4);
        try {
            RequestContext context = ctx(errReq);
            DeferredReply deferred = context.defer();
            assertTrue(deferred.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true)));
            assertFalse(deferred.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "again", true)));

            Frame cancelReq = request(5);
            try {
                RequestContext cancel = ctx(cancelReq);
                DeferredReply cancelReply = cancel.defer();
                state.removeDeferred(5);
                assertFalse(cancelReply.cancel());
            } finally {
                cancelReq.close();
            }
        } finally {
            errReq.close();
        }
    }

    @Test
    void nullEncodingCodecProducesEmptyPayloads() throws Exception {
        // a codec that returns null from encode/encodeError exercises the empty-payload branches
        Frame replyReq = request(1);
        try {
            RequestContext context = new RequestContext(conn, state, new NullCodec(), replyReq, runtime,
                    System.nanoTime(), null, 1);
            context.reply(Error.getDefaultInstance());
            assertEquals(0, conn.frames.get(0).payload().length);
        } finally {
            replyReq.close();
        }

        Frame errReq = request(2);
        try {
            RequestContext context = new RequestContext(conn, state, new NullCodec(), errReq, runtime,
                    System.nanoTime(), null, 1);
            context.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true));
            assertEquals(0, conn.frames.get(1).payload().length);
        } finally {
            errReq.close();
        }

        runtime.attachPlayer(conn, state, 5, false);
        Frame pushReq = request(3);
        try {
            RequestContext context = new RequestContext(conn, state, new NullCodec(), pushReq, runtime,
                    System.nanoTime(), null, 1);
            long pushId = context.push(MSG, Error.getDefaultInstance(), 0);
            assertTrue(pushId > 0);
            assertEquals(0, conn.frames.get(2).payload().length);
        } finally {
            pushReq.close();
        }
    }

    @Test
    void slowLoggingFastPathReturnsEarly() throws Exception {
        DispatcherRuntime fastRuntime = new DispatcherRuntime(new RouteScanner().scan(List.of()),
                new ProtobufPayloadCodec(), shards, config(5_000, 1, RawPayloadMode.RETAIN));
        fastRuntime.onConnect(conn);
        ConnectionState fastState = fastRuntime.stateForTest(conn.id());
        Frame req = request(1);
        try {
            // duration is far below the 1ms threshold, so maybeLogSlow returns before logging
            RequestContext context = new RequestContext(conn, fastState, new ProtobufPayloadCodec(),
                    req, fastRuntime, System.nanoTime(), null, 1);
            context.reply(Error.getDefaultInstance());
        } finally {
            req.close();
        }
    }

    @Test
    void needLoginClosesConnectionWhenConfigured() throws Exception {
        TestConnection closing = new TestConnection(2);
        DispatcherRuntime closeRuntime = new DispatcherRuntime(new RouteScanner().scan(List.of()),
                new ProtobufPayloadCodec(), shards, new DispatcherConfig(2, 10, RawPayloadMode.RETAIN,
                true, 5_000, 0, true, 8, 30_000, 4, 60_000));
        closeRuntime.onConnect(closing);
        ConnectionState closeState = closeRuntime.stateForTest(closing.id());
        Frame req = request(1);
        try {
            RequestContext context = new RequestContext(closing, closeState, new ProtobufPayloadCodec(),
                    req, closeRuntime, System.nanoTime(), null, 1);
            context.needLogin();
            assertFalse(closing.isActive());
        } finally {
            req.close();
        }

        // the default config does not close on needLogin
        Frame keepReq = request(2);
        try {
            RequestContext context = ctx(keepReq);
            context.needLogin();
            assertTrue(conn.isActive());
        } finally {
            keepReq.close();
        }
    }

    @Test
    void deferredErrorRejectsRemovedAndExpiredEntries() throws Exception {
        Frame removedReq = request(4);
        try {
            RequestContext context = ctx(removedReq);
            DeferredReply deferred = context.defer();
            state.removeDeferred(4); // simulate the entry already consumed
            assertFalse(deferred.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true)));
        } finally {
            removedReq.close();
        }

        Frame expiredReq = request(5);
        try {
            RequestContext context = ctx(expiredReq);
            DeferredReply deferred = context.defer();
            state.detachPlayer(); // epoch advanced after deferral
            assertFalse(deferred.error(CivError.of(CivErrorCodes.UNAUTHORIZED, "boom", true)));
        } finally {
            expiredReq.close();
        }
    }

    private static void assertSamePayload(Frame req, ByteBuf payload) {
        assertEquals(req.payload().readableBytes(), payload.readableBytes());
    }

    private static final class NullCodec implements io.github.cuihairu.civgenesis.core.codec.PayloadCodec {
        @Override
        public <T> T decode(int msgId, Class<T> targetType, ByteBuf payload) {
            return null;
        }

        @Override
        public ByteBuf encode(ByteBufAllocator alloc, Object message) {
            return null;
        }

        @Override
        public ByteBuf encodeError(ByteBufAllocator alloc, CivError error) {
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
}
