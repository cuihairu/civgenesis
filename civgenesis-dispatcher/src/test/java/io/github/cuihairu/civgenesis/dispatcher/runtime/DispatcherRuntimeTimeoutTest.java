package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.github.cuihairu.civgenesis.protocol.system.Error;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class DispatcherRuntimeTimeoutTest {
    @Test
    void deferredRequestTimesOutWithError() throws Exception {
        RouteTable routes = new RouteScanner().scan(List.of(new DeferController()));
        DispatcherConfig config = new DispatcherConfig(
                64,
                2048,
                RawPayloadMode.RETAIN,
                false,
                50,
                0,
                true,
                1024,
                30_000,
                2000,
                60_000
        );
        try (ShardExecutor shards = new ShardExecutor(1, "test-shard-")) {
            DispatcherRuntime dispatcher = new DispatcherRuntime(routes, new ProtobufPayloadCodec(), shards, config);

            TestConnection conn = new TestConnection(1);
            dispatcher.onConnect(conn);

            Frame req = new Frame(FrameType.REQ, 10, 1, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate());
            try {
                dispatcher.handle(conn, req);
                long deadline = System.currentTimeMillis() + 2000;
                while (conn.frames.stream().noneMatch(f -> f.type == FrameType.RESP) && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }
            } finally {
                req.close();
            }

            CapturedFrame resp = conn.frames.stream().filter(f -> f.type == FrameType.RESP).findFirst().orElse(null);
            assertNotNull(resp);
            assertEquals(10, resp.msgId);
            assertEquals(1, resp.seq);
            assertTrue((resp.flags & io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags.ERROR) != 0);

            Error err = Error.parseFrom(resp.payloadBytes);
            assertEquals(io.github.cuihairu.civgenesis.core.error.CivErrorCodes.REQUEST_TIMEOUT, err.getCode());
        }
    }

    @GameController
    public static final class DeferController {
        @GameRoute(id = 10, open = true)
        public void defer(RequestContext ctx, io.netty.buffer.ByteBuf payload) {
            ctx.defer();
        }
    }

    static final class CapturedFrame {
        final FrameType type;
        final int msgId;
        final long seq;
        final long flags;
        final byte[] payloadBytes;

        CapturedFrame(FrameType type, int msgId, long seq, long flags, byte[] payloadBytes) {
            this.type = type;
            this.msgId = msgId;
            this.seq = seq;
            this.flags = flags;
            this.payloadBytes = payloadBytes;
        }
    }

    static final class TestConnection implements Connection {
        private final long id;
        private volatile boolean active = true;
        private final CopyOnWriteArrayList<CapturedFrame> frames = new CopyOnWriteArrayList<>();

        TestConnection(long id) {
            this.id = id;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public ByteBufAllocator alloc() {
            return UnpooledByteBufAllocator.DEFAULT;
        }

        @Override
        public void send(Frame frame) {
            byte[] payloadBytes;
            if (frame.payload() == null || !frame.payload().isReadable()) {
                payloadBytes = new byte[0];
            } else {
                payloadBytes = ByteBufUtil.getBytes(frame.payload(), frame.payload().readerIndex(), frame.payload().readableBytes(), false);
            }
            frames.add(new CapturedFrame(frame.type(), frame.msgId(), frame.seq(), frame.flags(), payloadBytes));
            frame.close();
        }

        @Override
        public void close() {
            active = false;
        }
    }
}
