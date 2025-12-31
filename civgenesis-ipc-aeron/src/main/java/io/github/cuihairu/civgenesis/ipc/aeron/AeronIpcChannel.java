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

public final class AeronIpcChannel implements IpcLink, Closeable {
    private static final Logger log = LoggerFactory.getLogger(AeronIpcChannel.class);

    private final Publication pub;
    private final Subscription sub;
    private final int fragmentLimit;
    private volatile boolean closed;

    public AeronIpcChannel(Aeron aeron, int streamId, IpcMessageHandler handler) {
        this(aeron, "aeron:ipc", streamId, handler, 10);
    }

    public AeronIpcChannel(Aeron aeron, String channel, int streamId, IpcMessageHandler handler, int fragmentLimit) {
        Objects.requireNonNull(aeron, "aeron");
        Objects.requireNonNull(channel, "channel");
        this.fragmentLimit = Math.max(1, fragmentLimit);
        this.pub = aeron.addPublication(channel, streamId);
        this.sub = aeron.addSubscription(channel, streamId);

        FragmentHandler fragmentHandler = (DirectBuffer buffer, int offset, int length, io.aeron.logbuffer.Header header) -> {
            int selfSessionId = pub.sessionId();
            if (selfSessionId != 0 && header.sessionId() == selfSessionId) {
                return;
            }
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

        Thread.ofVirtual().name("civgenesis-aeron-poll").start(() -> {
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
