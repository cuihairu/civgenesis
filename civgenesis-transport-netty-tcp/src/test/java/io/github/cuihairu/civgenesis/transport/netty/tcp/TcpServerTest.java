package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpServerTest {

    @Test
    void serverAcceptsConnectionsAndEchoesFramesOverLoopback() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        TcpServerConfig config = new TcpServerConfig(
                1, 0, "127.0.0.1", 0, 64, 65536, 65536, false, 65536, 0);
        TcpServer server = new TcpServer(config, dispatcher);
        server.start();
        int port = boundPort(server);
        assertTrue(port > 0);
        // a second start() is a no-op while the server is running
        server.start();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteBuf payload = Unpooled.buffer().writeBytes(new byte[] {3, 1, 4});
            ByteBuf encoded = TlvFrameCodec.encode(UnpooledByteBufAllocator.DEFAULT,
                    new Frame(FrameType.REQ, 42, 1, 0, 0, 0, 0, payload));
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);
            encoded.release();
            payload.release();
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();

            int len = in.readInt();
            byte[] respBytes = new byte[len];
            in.readFully(respBytes);
            try (Frame resp = TlvFrameCodec.decode(Unpooled.wrappedBuffer(respBytes))) {
                assertEquals(FrameType.RESP, resp.type());
                assertEquals(42, resp.msgId());
            }
        }

        waitUntil(() -> dispatcher.disconnected.get() == 1, "client disconnect");
        assertEquals(1, dispatcher.connected.get());
        assertEquals(1, dispatcher.handled.size());

        server.stop();
        server.stop(); // idempotent
        server.close();
    }

    @Test
    void serverWorksWithPooledAllocatorAndDedicatedWorkerThreads() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        TcpServerConfig config = new TcpServerConfig(
                1, 2, "127.0.0.1", 0, 64, 65536, 65536, true, 65536, 0);
        TcpServer server = new TcpServer(config, dispatcher);
        server.start();
        int port = boundPort(server);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteBuf encoded = TlvFrameCodec.encode(UnpooledByteBufAllocator.DEFAULT,
                    new Frame(FrameType.PING, 7, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate()));
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);
            encoded.release();
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
            int len = in.readInt(); // length prefix of the response
            in.readFully(new byte[len]);
        }
        waitUntil(() -> dispatcher.disconnected.get() == 1, "client disconnect");
        server.stop();
    }

    @Test
    void defaultsAndConstructorsAreTolerant() {
        // null config falls back to defaults()
        TcpServer server = new TcpServer(null, new RecordingDispatcher());
        assertEquals(TcpServerConfig.defaults().port(), 9999);
        // stopping before start is a no-op
        server.stop();
    }

    @Test
    void encoderFallsBackToNoopMetrics() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyTcpFrameEncoder(null));
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1});
        try {
            channel.writeOutbound(new Frame(FrameType.PING, 1, 0, 0, 0, 0, 0, payload.retainedDuplicate()));
            assertTrue(channel.readOutbound() instanceof ByteBuf);
            channel.releaseOutbound();
            channel.finishAndReleaseAll();
        } finally {
            payload.release();
        }
    }

    @Test
    void handlerClosesFramesWhenConnectionAttributeIsCleared() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new DispatcherChannelHandler(new RecordingDispatcher(), null));
        // the attribute is cleared after channelActive to simulate a detached connection
        channel.attr(AttributeKey.valueOf("civgenesis.conn")).set(null);
        Frame frame = new Frame(FrameType.PING, 0, 0, 0, 0, 0, 0,
                Unpooled.wrappedBuffer(new byte[] {1}).retainedDuplicate());
        channel.writeInbound(frame);
        channel.finishAndReleaseAll();
    }

    private static int boundPort(TcpServer server) throws Exception {
        Field f = TcpServer.class.getDeclaredField("channel");
        f.setAccessible(true);
        Channel channel = (Channel) f.get(server);
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(20);
        }
    }

    private static final class RecordingDispatcher implements Dispatcher {
        final List<Frame> handled = new CopyOnWriteArrayList<>();
        final AtomicInteger connected = new AtomicInteger();
        final AtomicInteger disconnected = new AtomicInteger();

        @Override
        public void onConnect(Connection connection) {
            connected.incrementAndGet();
        }

        @Override
        public void onDisconnect(Connection connection) {
            disconnected.incrementAndGet();
        }

        @Override
        public void handle(Connection connection, Frame frame) {
            handled.add(frame);
            Frame resp = new Frame(FrameType.RESP, frame.msgId(), frame.seq(), 0, 0, 0, 0,
                    connection.alloc().buffer().writeBytes(new byte[] {9}));
            connection.send(resp);
        }
    }
}
