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

    @Test
    void rejectsMessageLiteTypeWithoutDefaultInstance() throws Exception {
        // the MessageLite interface itself passes the assignability check but has no
        // static getDefaultInstance(), so parser resolution fails and reports the class
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                codec.decode(SystemMsgIds.RESUME, com.google.protobuf.MessageLite.class,
                        Unpooled.wrappedBuffer(new byte[] {1})));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("com.google.protobuf.MessageLite"));
    }

    @Test
    void rejectsMessageLiteTypeWhoseDefaultInstanceIsNotAMessage() throws Exception {
        // a MessageLite-assignable type whose getDefaultInstance() does not return a
        // MessageLite also fails parser resolution
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                codec.decode(SystemMsgIds.RESUME, FakeMessage.class, Unpooled.wrappedBuffer(new byte[] {1})));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("FakeMessage"));
    }

    private static final class FakeMessage implements com.google.protobuf.MessageLite {
        public static Object getDefaultInstance() {
            return new Object();
        }

        @Override
        public void writeTo(com.google.protobuf.CodedOutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSerializedSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.google.protobuf.Parser<? extends com.google.protobuf.MessageLite> getParserForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.google.protobuf.ByteString toByteString() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(java.io.OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeDelimitedTo(java.io.OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.google.protobuf.MessageLite.Builder newBuilderForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.google.protobuf.MessageLite.Builder toBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInitialized() {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.google.protobuf.MessageLite getDefaultInstanceForType() {
            throw new UnsupportedOperationException();
        }
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
