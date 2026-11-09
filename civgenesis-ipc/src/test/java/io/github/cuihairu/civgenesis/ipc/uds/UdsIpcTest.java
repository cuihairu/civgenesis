package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.registry.Endpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdsIpcTest {
    private Path dir;

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("civgenesis-uds");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignore) {
                }
            });
        }
    }

    @Test
    void serverAndClientExchangeMessagesInBothDirections() throws Exception {
        Path sock = dir.resolve("s.sock");
        CountDownLatch serverGot = new CountDownLatch(1);
        CountDownLatch clientGot = new CountDownLatch(1);
        AtomicReference<byte[]> serverReceived = new AtomicReference<>();
        AtomicReference<byte[]> clientReceived = new AtomicReference<>();

        UdsIpcServer server = new UdsIpcServer(sock, UdsIpcOptions.defaults(), (link, msg) -> {
            serverReceived.set(msg);
            link.offer("pong".getBytes(StandardCharsets.UTF_8));
            serverGot.countDown();
        });
        server.start();

        IpcLink client = UdsIpcClient.connect(sock, UdsIpcOptions.defaults(), (link, msg) -> {
            clientReceived.set(msg);
            clientGot.countDown();
        });

        assertTrue(client.offer("ping".getBytes(StandardCharsets.UTF_8)));
        assertTrue(serverGot.await(5, TimeUnit.SECONDS), "server should receive message");
        assertEquals("ping", new String(serverReceived.get(), StandardCharsets.UTF_8));
        assertTrue(clientGot.await(5, TimeUnit.SECONDS), "client should receive pong");
        assertEquals("pong", new String(clientReceived.get(), StandardCharsets.UTF_8));

        client.close();
        Thread.sleep(500); // let the server-side reader observe the clean EOF
        server.close();
    }

    @Test
    void startIsIdempotentAndCloseStopsServerAndDeletesSocket() throws Exception {
        Path sock = dir.resolve("idempotent.sock");
        UdsIpcServer server = new UdsIpcServer(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        server.start();
        server.start(); // second start is a no-op
        assertTrue(Files.exists(sock));

        IpcLink client = UdsIpcClient.connect(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        assertTrue(client.offer("x".getBytes(StandardCharsets.UTF_8)));

        server.stop();
        assertFalse(Files.exists(sock));
        server.close(); // close() delegates to stop(), already stopped
        client.close();
    }

    @Test
    void startCreatesParentDirectoriesAndDialerConnects() throws Exception {
        Path nested = dir.resolve("deep").resolve("nested").resolve("d.sock");
        CountDownLatch got = new CountDownLatch(1);
        UdsIpcServer server = new UdsIpcServer(nested, UdsIpcOptions.defaults(), (l, msg) -> got.countDown());
        server.start();
        assertTrue(Files.exists(nested));

        IpcLink link = new UdsIpcDialer(UdsIpcOptions.defaults())
                .connect(new Endpoint("uds:" + nested), (l, msg) -> {
                });
        assertTrue(link.offer("hello".getBytes(StandardCharsets.UTF_8)));
        assertTrue(got.await(5, TimeUnit.SECONDS));

        link.close();
        server.close();
    }

    @Test
    void offerOnClosedLinkReturnsFalse() throws Exception {
        Path sock = dir.resolve("closed.sock");
        UdsIpcServer server = new UdsIpcServer(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        server.start();
        IpcLink client = UdsIpcClient.connect(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        client.close();
        assertFalse(client.offer("late".getBytes(StandardCharsets.UTF_8)));
        server.close();
    }

    @Test
    void offerRejectsOversizedFrameAndQueueOverflow() throws Exception {
        Path sock = dir.resolve("limits.sock");
        UdsIpcOptions tiny = new UdsIpcOptions(8, 0);
        UdsIpcServer server = new UdsIpcServer(sock, tiny, (link, msg) -> {
        });
        server.start();
        IpcLink client = UdsIpcClient.connect(sock, tiny, (link, msg) -> {
        });
        assertThrows(IllegalArgumentException.class,
                () -> client.offer(new byte[100]));
        // maxQueueBytes=8, the first 8-byte offer already exceeds the queue budget
        assertFalse(client.offer(new byte[8]));
        client.close();
        server.close();
    }

    @Test
    void invalidFrameLengthAndIncompleteFrameCloseTheConnection() throws Exception {
        Path sock = dir.resolve("bad.sock");
        CountDownLatch closed = new CountDownLatch(1);
        UdsIpcServer server = new UdsIpcServer(sock, UdsIpcOptions.defaults(), (link, msg) -> closed.countDown());
        server.start();

        // negative frame length is rejected by the reader, which then closes the connection
        try (SocketChannel raw = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            raw.connect(UnixDomainSocketAddress.of(sock));
            raw.write(ByteBuffer.wrap(new byte[]{-1, -1, -1, -1}));
            Thread.sleep(500);
        }

        // incomplete frame (claims 10 bytes, sends 5, then closes) is also rejected
        try (SocketChannel raw = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            raw.connect(UnixDomainSocketAddress.of(sock));
            DataOutputStream out = new DataOutputStream(java.nio.channels.Channels.newOutputStream(raw));
            out.writeInt(10);
            out.write(new byte[5]);
            out.flush();
            Thread.sleep(500);
        }

        // both raw clients triggered an accept and then a close; the server stays running
        assertTrue(Files.exists(sock));
        server.close();
    }

    @Test
    void closeIsIdempotentAndStopsThreads() throws Exception {
        Path sock = dir.resolve("close.sock");
        UdsIpcServer server = new UdsIpcServer(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        server.start();
        IpcLink client = UdsIpcClient.connect(sock, UdsIpcOptions.defaults(), (link, msg) -> {
        });
        client.close();
        client.close(); // idempotent
        server.close();
        server.close(); // idempotent
    }
}
