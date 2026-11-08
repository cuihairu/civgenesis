package io.github.cuihairu.civgenesis.system.controller;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class SystemTestConnection implements Connection {
    final long id;
    final AtomicBoolean active = new AtomicBoolean(true);
    final List<CapturedFrame> frames = new CopyOnWriteArrayList<>();

    SystemTestConnection(long id) {
        this.id = id;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public ByteBufAllocator alloc() {
        return UnpooledByteBufAllocator.DEFAULT;
    }

    @Override
    public void send(Frame frame) {
        byte[] payload = frame.payload() == null || !frame.payload().isReadable()
                ? new byte[0]
                : ByteBufUtil.getBytes(frame.payload(), frame.payload().readerIndex(), frame.payload().readableBytes(), false);
        frames.add(new CapturedFrame(frame.type(), frame.msgId(), frame.flags(), payload));
        frame.close();
    }

    @Override
    public void close() {
        active.set(false);
    }

    record CapturedFrame(FrameType type, int msgId, long flags, byte[] payload) {}
}
