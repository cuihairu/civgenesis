package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class UdsIpcServer implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(UdsIpcServer.class);

    private final Path path;
    private final UdsIpcOptions options;
    private final IpcMessageHandler handler;
    private final CopyOnWriteArrayList<UdsIpcConnection> connections = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private Thread acceptThread;
    private ServerSocketChannel server;

    public UdsIpcServer(Path path, UdsIpcOptions options, IpcMessageHandler handler) {
        this.path = Objects.requireNonNull(path, "path");
        this.options = Objects.requireNonNullElse(options, UdsIpcOptions.defaults());
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(path));
        running = true;

        acceptThread = Thread.ofPlatform().daemon(true).name("civgenesis-uds-accept").start(() -> {
            while (running) {
                try {
                    SocketChannel ch = server.accept();
                    UdsIpcConnection conn = new UdsIpcConnection(ch, options, handler);
                    connections.add(conn);
                } catch (IOException e) {
                    if (running) {
                        log.warn("uds accept failed", e);
                    }
                }
            }
        });
    }

    public synchronized void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignore) {
            }
            server = null;
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        for (UdsIpcConnection c : connections) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
        connections.clear();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignore) {
        }
    }

    @Override
    public void close() {
        stop();
    }
}
