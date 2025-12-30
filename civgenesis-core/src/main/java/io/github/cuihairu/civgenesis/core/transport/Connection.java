package io.github.cuihairu.civgenesis.core.transport;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.netty.buffer.ByteBufAllocator;

public interface Connection {
    long id();

    boolean isActive();

    ByteBufAllocator alloc();

    void send(Frame frame);

    void close();
}

