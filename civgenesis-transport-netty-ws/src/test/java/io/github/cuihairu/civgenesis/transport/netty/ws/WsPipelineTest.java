package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsPipelineTest {
    @Test
    void decodesEncodesAndTracksFrames() {
        RecordingMetrics metrics = new RecordingMetrics();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyWsFrameEncoder(metrics), new NettyWsFrameDecoder(metrics));
        try (Frame frame = new Frame(FrameType.REQ, 7, 8, 0, 0, 0, 0, Unpooled.wrappedBuffer(new byte[] {1}).retainedDuplicate())) {
            channel.writeOutbound(frame);
            BinaryWebSocketFrame encoded = channel.readOutbound();
            channel.writeInbound(new BinaryWebSocketFrame(encoded.content().retain()));
            Frame decoded = channel.readInbound();
            assertEquals(FrameType.REQ, decoded.type());
            assertEquals(7, decoded.msgId());
            assertEquals(8, decoded.seq());
            decoded.close();
            assertEquals(2, metrics.frames.get());
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void dispatcherLifecycleClosesMissingConnectionFrames() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingMetrics metrics = new RecordingMetrics();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                ctx.fireChannelActive();
            }
        }, new DispatcherChannelHandler(dispatcher, metrics));
        Frame frame = new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0, Unpooled.wrappedBuffer(new byte[] {1}).retainedDuplicate());
        channel.writeInbound(frame);
        assertEquals(1, dispatcher.connected);
        assertEquals(1, dispatcher.handled.size());
        channel.finish();
        channel.close().awaitUninterruptibly();
        assertEquals(1, dispatcher.disconnected);
        assertEquals(1, metrics.opened.get());
        assertEquals(1, metrics.closed.get());
    }

    @Test
    void pingBeforeCloseCancelsOrClosesByConfiguration() {
        EmbeddedChannel disabled = new EmbeddedChannel(new PingBeforeCloseHandler(false, 1000), new ChannelInboundHandlerAdapter());
        disabled.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertFalse(disabled.isOpen());

        EmbeddedChannel enabled = new EmbeddedChannel(new PingBeforeCloseHandler(true, 5), new ChannelInboundHandlerAdapter());
        enabled.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        Object ping = enabled.readOutbound();
        assertTrue(ping instanceof PingWebSocketFrame);
        enabled.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        enabled.writeInbound(new PongWebSocketFrame());
        enabled.runPendingTasks();
        assertTrue(enabled.isOpen());
        enabled.finishAndReleaseAll();
    }

    @Test
    void connectionDelegatesChannelLifecycle() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        Connection connection = new NettyWsConnection(8, channel);
        assertEquals(8, connection.id());
        assertTrue(connection.isActive());
        try (Frame frame = new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate())) {
            connection.send(frame);
        }
        assertEquals(1, channel.outboundMessages().size());
        connection.close();
        assertFalse(connection.isActive());
    }

    @Test
    void serverConfigurationAndLifecycle() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        WsServerConfig config = new WsServerConfig(1, 1, 0, "/", 128, 0, 0, false, 1024, 1, true, 1);
        WsServer server = new WsServer(config, dispatcher, new RecordingMetrics());
        server.start();
        server.start();
        server.stop();
        server.close();
        assertEquals(WsServerConfig.defaults().path(), "/");
        assertEquals(8888, WsServerConfig.defaults().port());
        assertTrue(WsServerConfig.defaults().pooledAllocator());
    }

    private static final class RecordingMetrics implements CivMetrics {
        final AtomicInteger opened = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger frames = new AtomicInteger();

        @Override
        public void onFrameIn(String transport, FrameType type, int msgId, int bytes) {
            frames.incrementAndGet();
        }

        @Override
        public void onFrameOut(String transport, FrameType type, int msgId, int bytes) {
            frames.incrementAndGet();
        }

        @Override
        public void onConnectionOpen(String transport) {
            opened.incrementAndGet();
        }

        @Override
        public void onConnectionClose(String transport) {
            closed.incrementAndGet();
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
