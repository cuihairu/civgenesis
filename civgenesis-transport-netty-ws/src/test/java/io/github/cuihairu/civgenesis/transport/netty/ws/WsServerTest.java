package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsServerTest {

    @Test
    void serverCompletesHandshakeAndEchoesFramesOverLoopback() throws Exception {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        WsServerConfig config = new WsServerConfig(
                1, 0, 0, "/ws", 64, 65536, 65536, false, 65536, 0, true, 50);
        WsServer server = new WsServer(config, dispatcher);
        server.start();
        int port = boundPort(server);
        assertTrue(port > 0);
        server.start(); // second start is a no-op while running

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(("GET /ws HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String handshake = readHttpHeaders(in);
            assertTrue(handshake.startsWith("HTTP/1.1 101"), handshake);

            byte[] payload = tlvBytes(new Frame(FrameType.REQ, 42, 1, 0, 0, 0, 0,
                    Unpooled.buffer().writeBytes(new byte[] {3, 1, 4})));
            writeMaskedBinaryFrame(out, payload);

            byte[] respPayload = readBinaryFrame(in);
            try (Frame resp = TlvFrameCodec.decode(Unpooled.wrappedBuffer(respPayload))) {
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
        WsServerConfig config = new WsServerConfig(
                1, 2, 0, "/", 64, 65536, 65536, true, 65536, 0, true, 50);
        WsServer server = new WsServer(config, dispatcher);
        server.start();
        int port = boundPort(server);

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("GET / HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertTrue(readHttpHeaders(in).startsWith("HTTP/1.1 101"));
            writeMaskedBinaryFrame(out, tlvBytes(new Frame(FrameType.PING, 5, 0, 0, 0, 0, 0,
                    Unpooled.EMPTY_BUFFER.retainedDuplicate())));
            readBinaryFrame(in); // the dispatcher's RESP frame
        }
        waitUntil(() -> dispatcher.disconnected.get() == 1, "client disconnect");
        server.stop();
    }

    @Test
    void encoderAndDecoderTolerateNullAndThrowingMetrics() {
        // null metrics fall back to the noop implementation
        EmbeddedChannel channel = new EmbeddedChannel(new NettyWsFrameEncoder(null), new NettyWsFrameDecoder(null));
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1});
        try {
            channel.writeOutbound(new Frame(FrameType.PING, 1, 0, 0, 0, 0, 0, payload.retainedDuplicate()));
            BinaryWebSocketFrame encoded = channel.readOutbound();
            channel.writeInbound(new BinaryWebSocketFrame(encoded.content().retain()));
            Frame decoded = channel.readInbound();
            assertEquals(1, decoded.msgId());
            decoded.close();
            channel.releaseOutbound();
            channel.finishAndReleaseAll();
        } finally {
            payload.release();
        }
        // a metrics failure must release the encoded buffer and propagate
        EmbeddedChannel throwing = new EmbeddedChannel(new NettyWsFrameEncoder(new ThrowingMetrics()));
        ByteBuf badPayload = Unpooled.wrappedBuffer(new byte[] {1});
        boolean threw = false;
        try {
            throwing.writeOutbound(new Frame(FrameType.PING, 1, 0, 0, 0, 0, 0, badPayload.retainedDuplicate()));
        } catch (RuntimeException ignored) {
            threw = true;
        }
        assertTrue(threw, "the metrics failure must propagate");
        throwing.finish();
        badPayload.release();
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

    @Test
    void defaultsAndNoopConstructorAreAvailable() {
        // null config falls back to defaults()
        WsServer server = new WsServer(null, new RecordingDispatcher());
        assertEquals(WsServerConfig.defaults().port(), 8888);
        // stopping before start is a no-op
        server.stop();
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

    @Test
    void pingHandlerForwardsNonIdleEvents() {
        EmbeddedChannel channel = new EmbeddedChannel(new PingBeforeCloseHandler(true, 50));
        channel.pipeline().fireUserEventTriggered(new Object());
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void connectionExposesChannelOperations() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyWsConnection connection = new NettyWsConnection(7, channel);
        assertEquals(7, connection.id());
        assertTrue(connection.isActive());
        assertTrue(connection.alloc() == channel.alloc());
        try (Frame frame = new Frame(FrameType.PONG, 0, 0, 0, 0, 0, 0,
                Unpooled.EMPTY_BUFFER.retainedDuplicate())) {
            connection.send(frame);
        }
        assertEquals(1, channel.outboundMessages().size());
        channel.finishAndReleaseAll();
        connection.close();
        assertTrue(!connection.isActive());
    }

    private static int boundPort(WsServer server) throws Exception {
        Field f = WsServer.class.getDeclaredField("channel");
        f.setAccessible(true);
        Channel channel = (Channel) f.get(server);
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    private static byte[] tlvBytes(Frame frame) {
        ByteBuf encoded = TlvFrameCodec.encode(UnpooledByteBufAllocator.DEFAULT, frame);
        byte[] bytes = new byte[encoded.readableBytes()];
        encoded.readBytes(bytes);
        encoded.release();
        frame.close();
        return bytes;
    }

    private static void writeMaskedBinaryFrame(OutputStream out, byte[] payload) throws Exception {
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        out.write(new byte[] {(byte) 0x82, (byte) (0x80 | payload.length)});
        out.write(mask);
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        out.write(masked);
        out.flush();
    }

    private static byte[] readBinaryFrame(InputStream in) throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        assertEquals(0x82, b0, "expected a FIN binary frame");
        int len = b1 & 0x7F;
        assertTrue(len < 126, "test payloads stay within a single length byte");
        byte[] payload = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(payload, read, len - read);
            if (n < 0) {
                throw new IllegalStateException("stream ended early");
            }
            read += n;
        }
        return payload;
    }

    private static String readHttpHeaders(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int prev = 0;
        while (true) {
            int c = in.read();
            if (c < 0) {
                break;
            }
            sb.append((char) c);
            if (prev == '\r' && c == '\n' && sb.toString().endsWith("\r\n\r\n")) {
                break;
            }
            prev = c;
        }
        return sb.toString();
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
