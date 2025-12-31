package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;

import java.util.Objects;

final class NettyTcpConnection implements Connection {
    private final long id;
    private final Channel channel;

    NettyTcpConnection(long id, Channel channel) {
        this.id = id;
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public ByteBufAllocator alloc() {
        return channel.alloc();
    }

    @Override
    public void send(Frame frame) {
        channel.writeAndFlush(frame);
    }

    @Override
    public void close() {
        channel.close();
    }
}

