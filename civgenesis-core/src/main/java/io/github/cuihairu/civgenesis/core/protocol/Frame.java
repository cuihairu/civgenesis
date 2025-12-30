package io.github.cuihairu.civgenesis.core.protocol;

import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class Frame implements AutoCloseable {
    private final FrameType type;
    private final int msgId;
    private final long seq;
    private final long pushId;
    private final long flags;
    private final long ts;
    private final long ackPushId;
    private final ByteBuf payload;

    public Frame(
            FrameType type,
            int msgId,
            long seq,
            long pushId,
            long flags,
            long ts,
            long ackPushId,
            ByteBuf payload
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.msgId = msgId;
        this.seq = seq;
        this.pushId = pushId;
        this.flags = flags;
        this.ts = ts;
        this.ackPushId = ackPushId;
        this.payload = payload;
    }

    public FrameType type() {
        return type;
    }

    public int msgId() {
        return msgId;
    }

    public long seq() {
        return seq;
    }

    public long pushId() {
        return pushId;
    }

    public long flags() {
        return flags;
    }

    public long ts() {
        return ts;
    }

    public long ackPushId() {
        return ackPushId;
    }

    public ByteBuf payload() {
        return payload;
    }

    public boolean hasFlag(long flag) {
        return (flags & flag) != 0;
    }

    @Override
    public void close() {
        if (payload != null) {
            payload.release();
        }
    }
}

