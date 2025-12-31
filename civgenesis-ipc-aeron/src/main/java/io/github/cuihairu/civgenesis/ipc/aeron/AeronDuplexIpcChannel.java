package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Objects;

public final class AeronDuplexIpcChannel implements IpcLink, Closeable {
    private static final Logger log = LoggerFactory.getLogger(AeronDuplexIpcChannel.class);

    private final Publication pub;
    private final Subscription sub;
    private final int fragmentLimit;
    private volatile boolean closed;

    public AeronDuplexIpcChannel(Aeron aeron, int inStreamId, int outStreamId, IpcMessageHandler handler) {
        this(aeron, "aeron:ipc", inStreamId, outStreamId, handler, 10);
    }

    public AeronDuplexIpcChannel(Aeron aeron, String channel, int inStreamId, int outStreamId, IpcMessageHandler handler, int fragmentLimit) {
        Objects.requireNonNull(aeron, "aeron");
        Objects.requireNonNull(channel, "channel");
        if (inStreamId <= 0 || outStreamId <= 0) {
            throw new IllegalArgumentException("streamId must be > 0");
        }
        this.fragmentLimit = Math.max(1, fragmentLimit);
        this.pub = aeron.addPublication(channel, outStreamId);
        this.sub = aeron.addSubscription(channel, inStreamId);

        FragmentHandler fragmentHandler = (DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) -> {
            if (handler == null) {
                return;
            }
            byte[] msg = new byte[length];
            buffer.getBytes(offset, msg);
            try {
                handler.onMessage(this, msg);
            } catch (Exception e) {
                log.warn("aeron onMessage failed", e);
            }
        };

        Thread.ofVirtual().name("civgenesis-aeron-duplex-poll").start(() -> {
            while (!closed) {
                try {
                    sub.poll(fragmentHandler, this.fragmentLimit);
                    Thread.onSpinWait();
                } catch (Exception e) {
                    if (!closed) {
                        log.warn("aeron poll failed", e);
                    }
                }
            }
        });
    }

    @Override
    public boolean offer(byte[] message) {
        if (closed) {
            return false;
        }
        byte[] msg = Objects.requireNonNull(message, "message");
        UnsafeBuffer buffer = new UnsafeBuffer(msg);
        long r = pub.offer(buffer, 0, msg.length);
        return r > 0;
    }

    @Override
    public void close() {
        closed = true;
        try {
            pub.close();
        } catch (Exception ignore) {
        }
        try {
            sub.close();
        } catch (Exception ignore) {
        }
    }
}

