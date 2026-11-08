package io.github.cuihairu.civgenesis.codec.tlv;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolTags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlvFrameCodecMalformedTest {
    @Test
    void decodeRejectsUnknownType() {
        ByteBuf input = field(ProtocolTags.TYPE, 999).duplicate();
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(input));
    }

    @Test
    void decodeReleasesOnlyPayloadWhenTypeMissing() {
        ByteBuf input = Unpooled.compositeBuffer()
                .addComponent(true, field(ProtocolTags.PAYLOAD, 1).retain())
                .addComponent(true, Unpooled.wrappedBuffer(new byte[] {9}));
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(input));
    }

    @Test
    void decodeReplacesDuplicatePayloadWithLatest() {
        ByteBuf input = Unpooled.buffer();
        writePayloadField(input, new byte[] {1});
        input.writeBytes(field(ProtocolTags.TYPE, FrameType.REQ.id()));
        writePayloadField(input, new byte[] {2, 3});
        Frame decoded = TlvFrameCodec.decode(input);
        assertEquals(2, decoded.payload().readableBytes());
        assertEquals(2, decoded.payload().getByte(0));
        assertEquals(3, decoded.payload().getByte(1));
        decoded.close();
    }

    @Test
    void encodeClosesFrameAndFreesEncoderOnFailure() {
        ByteBuf payload = Unpooled.buffer();
        payload.writeBytes(new byte[] {1});
        try (Frame frame = new Frame(FrameType.REQ, 1, 1, 0, 0, 0, 0, payload.retainedDuplicate())) {
            assertThrows(RuntimeException.class, () -> TlvFrameCodec.encode(new FailingAllocator(), frame));
            assertTrue(frame.payload().refCnt() > 0);
        } finally {
            payload.release();
        }
    }

    private static ByteBuf field(int tag, int value) {
        ByteBuf out = Unpooled.buffer();
        Varints.writeUvarint(out, tag);
        Varints.writeUvarint(out, Varints.uvarintSize(value));
        Varints.writeUvarint(out, value);
        return out;
    }

    private static ByteBuf field(int tag, int value, ByteBuf payload) {
        ByteBuf out = field(tag, value);
        out.writeBytes(payload);
        return out;
    }

    private static void writePayloadField(ByteBuf out, byte[] payload) {
        Varints.writeUvarint(out, ProtocolTags.PAYLOAD);
        Varints.writeUvarint(out, payload.length);
        out.writeBytes(payload);
    }

    private static final class FailingAllocator extends io.netty.buffer.AbstractByteBufAllocator {
        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }

        @Override
        protected io.netty.buffer.ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            throw new RuntimeException("allocation failed");
        }

        @Override
        protected io.netty.buffer.ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            throw new RuntimeException("allocation failed");
        }
    }
}
