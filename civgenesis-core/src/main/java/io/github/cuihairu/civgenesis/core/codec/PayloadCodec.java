package io.github.cuihairu.civgenesis.core.codec;

import io.github.cuihairu.civgenesis.core.error.CivError;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public interface PayloadCodec {
    <T> T decode(int msgId, Class<T> targetType, ByteBuf payload) throws Exception;

    ByteBuf encode(ByteBufAllocator alloc, Object message) throws Exception;

    ByteBuf encodeError(ByteBufAllocator alloc, CivError error) throws Exception;
}

