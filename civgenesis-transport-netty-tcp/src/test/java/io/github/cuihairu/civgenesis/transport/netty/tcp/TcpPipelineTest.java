package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpPipelineTest {
    @Test
    void decodesEncodesAndTracksFramesWithoutRetainingPayload() {
        RecordingMetrics metrics = new RecordingMetrics();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyTcpFrameEncoder(metrics), new NettyTcpFrameDecoder(metrics));
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1, 2});
        // the encoder closes the frame payload, so the outbound frame is not closed here
        channel.writeOutbound(new Frame(FrameType.REQ, 7, 8, 9, 10, 11, 12, payload.retainedDuplicate()));
        ByteBuf encoded = channel.readOutbound();
        channel.writeInbound(encoded.retainedDuplicate());
        Frame decoded = channel.readInbound();
        try {
            assertEquals(FrameType.REQ, decoded.type());
            assertEquals(7, decoded.msgId());
            assertEquals(8, decoded.seq());
            assertEquals(9, decoded.pushId());
            assertEquals(10, decoded.flags());
            assertEquals(11, decoded.ts());
            assertEquals(12, decoded.ackPushId());
            assertEquals(2, decoded.payload().readableBytes());
            assertEquals(1, metrics.in.get());
            assertEquals(1, metrics.out.get());
        } finally {
            decoded.close();
            encoded.release();
            payload.release();
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void encoderClosesFrameAndPropagatesAllocatorFailure() {
        ByteBuf payload = Unpooled.buffer();
        payload.writeBytes(new byte[] {1});
        EmbeddedChannel channel = new EmbeddedChannel(new NettyTcpFrameEncoder(new ThrowingMetrics()));
        try {
            assertThrowsRuntimeException(() ->
                    channel.writeOutbound(new Frame(FrameType.REQ, 1, 1, 0, 0, 0, 0, payload.retainedDuplicate())));
            channel.finish();
        } finally {
            payload.release();
        }
    }

    @Test
    void decoderForwardsFrameAndConnectionSendsAndCloses() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyTcpFrameDecoder(null), new ChannelInitializer<>() {
            @Override
            protected void initChannel(io.netty.channel.Channel ch) {
                ch.pipeline().addLast(new DispatcherChannelHandler(dispatcher, new RecordingMetrics()));
            }
        });
        ByteBuf input = io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec.encode(channel.alloc(),
                new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
        channel.writeInbound(input);
        assertEquals(1, dispatcher.connected);
        assertEquals(1, dispatcher.handled.size());
        assertEquals(FrameType.PING, dispatcher.handled.get(0).type());
        channel.finishAndReleaseAll();
        assertEquals(1, dispatcher.disconnected);

        EmbeddedChannel outgoing = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof Frame frame) {
                    try {
                        ctx.write(io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec.encode(ctx.alloc(), frame), promise);
                    } catch (Exception e) {
                        ctx.write(Unpooled.EMPTY_BUFFER, promise);
                    }
                    return;
                }
                super.write(ctx, msg, promise);
            }
        });
        Connection connection = new NettyTcpConnection(9, outgoing);
        assertEquals(9, connection.id());
        assertTrue(connection.isActive());
        try (Frame frame = new Frame(FrameType.PONG, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate())) {
            connection.send(frame);
        }
        assertEquals(1, outgoing.outboundMessages().size());
        connection.close();
        assertFalse(connection.isActive());
    }

    @Test
    void handlerClosesFramesWhenConnectionAttributeMissing() {
        EmbeddedChannel channel = new EmbeddedChannel(new DispatcherChannelHandler(new RecordingDispatcher(), null));
        Frame frame = new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0, Unpooled.wrappedBuffer(new byte[] {1}).retainedDuplicate());
        channel.writeInbound(frame);
        channel.finishAndReleaseAll();
    }

    private static void assertThrowsRuntimeException(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            return;
        }
        throw new AssertionError("Expected RuntimeException");
    }

    private static final class RecordingMetrics implements CivMetrics {
        final AtomicInteger in = new AtomicInteger();
        final AtomicInteger out = new AtomicInteger();

        @Override
        public void onFrameIn(String transport, FrameType type, int msgId, int bytes) {
            in.incrementAndGet();
        }

        @Override
        public void onFrameOut(String transport, FrameType type, int msgId, int bytes) {
            out.incrementAndGet();
        }
    }

    private static final class ThrowingMetrics implements CivMetrics {
        @Override
        public void onFrameIn(String transport, FrameType type, int msgId, int bytes) {
            throw new IllegalStateException("metrics failure");
        }

        @Override
        public void onFrameOut(String transport, FrameType type, int msgId, int bytes) {
            throw new IllegalStateException("metrics failure");
        }
    }

    private static final class RecordingDispatcher implements Dispatcher {
        final List<Frame> handled = new java.util.concurrent.CopyOnWriteArrayList<>();
        int connected;
        int disconnected;

        @Override
        public void onConnect(Connection ignored) {
            connected++;
        }

        @Override
        public void onDisconnect(Connection ignored) {
            disconnected++;
        }

        @Override
        public void handle(Connection ignored, Frame frame) {
            handled.add(frame);
        }
    }
}
