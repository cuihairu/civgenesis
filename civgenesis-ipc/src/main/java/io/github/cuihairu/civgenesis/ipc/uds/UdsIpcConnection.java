package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class UdsIpcConnection implements IpcLink {
    private static final Logger log = LoggerFactory.getLogger(UdsIpcConnection.class);

    private final SocketChannel channel;
    private final UdsIpcOptions options;
    private final IpcMessageHandler handler;

    private final ArrayBlockingQueue<byte[]> outQueue;
    private final AtomicInteger queuedBytes = new AtomicInteger(0);

    private volatile boolean closed;
    private final Thread reader;
    private final Thread writer;

    UdsIpcConnection(SocketChannel channel, UdsIpcOptions options, IpcMessageHandler handler) throws IOException {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.options = Objects.requireNonNull(options, "options");
        this.handler = handler;
        this.outQueue = new ArrayBlockingQueue<>(1024);

        channel.configureBlocking(true);

        this.reader = Thread.ofVirtual().name("civgenesis-uds-reader").start(this::readLoop);
        this.writer = Thread.ofVirtual().name("civgenesis-uds-writer").start(this::writeLoop);
    }

    @Override
    public boolean offer(byte[] message) {
        if (closed) {
            return false;
        }
        byte[] msg = Objects.requireNonNull(message, "message");
        if (msg.length > options.maxFrameBytes()) {
            throw new IllegalArgumentException("frame too large: " + msg.length);
        }
        int after = queuedBytes.addAndGet(msg.length);
        if (after > options.maxQueueBytes()) {
            queuedBytes.addAndGet(-msg.length);
            return false;
        }
        boolean ok = outQueue.offer(msg);
        if (!ok) {
            queuedBytes.addAndGet(-msg.length);
        }
        return ok;
    }

    private void readLoop() {
        try (var in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)))) {
            while (!closed) {
                int len;
                try {
                    len = in.readInt();
                } catch (EOFException eof) {
                    return;
                }
                if (len < 0 || len > options.maxFrameBytes()) {
                    throw new IOException("invalid frame length: " + len);
                }
                byte[] buf = in.readNBytes(len);
                if (buf.length != len) {
                    throw new EOFException("incomplete frame");
                }
                if (handler != null) {
                    handler.onMessage(this, buf);
                }
            }
        } catch (Exception e) {
            if (!closed) {
                log.warn("uds read failed", e);
                close();
            }
        }
    }

    private void writeLoop() {
        try (var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)))) {
            while (!closed) {
                byte[] msg = outQueue.take();
                queuedBytes.addAndGet(-msg.length);
                out.writeInt(msg.length);
                out.write(msg);
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!closed) {
                log.warn("uds write failed", e);
                close();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        reader.interrupt();
        writer.interrupt();
        try {
            channel.close();
        } catch (IOException ignore) {
        }
    }
}

