package io.github.cuihairu.civgenesis.codec.protobuf;

import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.protocol.system.SystemMsgIds;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtobufPayloadCodecTest {
    private final ProtobufPayloadCodec codec = new ProtobufPayloadCodec();

    @Test
    void encodesAndDecodesMessage() throws Exception {
        var req = io.github.cuihairu.civgenesis.protocol.system.ResumeReq.newBuilder()
                .setToken("token")
                .build();
        var buf = codec.encode(UnpooledByteBufAllocatorHolder.ALLOCATOR, req);
        try {
            var decoded = codec.decode(SystemMsgIds.RESUME,
                    io.github.cuihairu.civgenesis.protocol.system.ResumeReq.class, buf.duplicate());
            assertEquals(req, decoded);
        } finally {
            buf.release();
        }
    }

    @Test
    void encodesErrorIncludingDetails() throws Exception {
        var buf = codec.encodeError(UnpooledByteBufAllocatorHolder.ALLOCATOR,
                new CivError(CivErrorCodes.NEED_LOGIN, "need login", true, Map.of("reason", "auth")));
        try {
            var decoded = io.github.cuihairu.civgenesis.protocol.system.Error.parseFrom(
                    UnpooledByteBufAllocatorHolder.bytes(buf));
            assertEquals(CivErrorCodes.NEED_LOGIN, decoded.getCode());
            assertEquals("need login", decoded.getMessage());
            assertTrueValue(decoded.getRetryable());
            assertEquals("auth", decoded.getDetailOrThrow("reason"));
        } finally {
            buf.release();
        }
    }

    @Test
    void validatesInputsAndTargets() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                codec.decode(SystemMsgIds.RESUME, String.class, Unpooled.wrappedBuffer(new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () ->
                codec.decode(SystemMsgIds.RESUME, io.github.cuihairu.civgenesis.protocol.system.ResumeReq.class, Unpooled.EMPTY_BUFFER));
        assertThrows(NullPointerException.class, () -> codec.encode(null, "text"));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(UnpooledByteBufAllocatorHolder.ALLOCATOR, "text"));
        assertThrows(NullPointerException.class, () -> codec.encodeError(UnpooledByteBufAllocatorHolder.ALLOCATOR, null));
    }

    private static void assertTrueValue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }

    private static final class UnpooledByteBufAllocatorHolder {
        static final io.netty.buffer.ByteBufAllocator ALLOCATOR =
                io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;

        private static byte[] bytes(io.netty.buffer.ByteBuf buf) {
            return io.netty.buffer.ByteBufUtil.getBytes(buf, buf.readerIndex(), buf.readableBytes(), false);
        }
    }
}
