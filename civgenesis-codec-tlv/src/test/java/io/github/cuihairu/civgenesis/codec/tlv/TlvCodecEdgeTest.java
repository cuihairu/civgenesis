package io.github.cuihairu.civgenesis.codec.tlv;

import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolTags;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlvCodecEdgeTest {

    @Test
    void decodeRejectsOverflowingTagAndLengthAndFrameIds() {
        // tag varint above Integer.MAX_VALUE
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(
                Unpooled.wrappedBuffer(varint(0x80000000L), varint(1), new byte[] {1})));
        // length varint above Integer.MAX_VALUE
        ByteBuf bigLength = Unpooled.buffer();
        Varints.writeUvarint(bigLength, ProtocolTags.TYPE);
        bigLength.writeBytes(varint(0x80000000L));
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(bigLength));
        bigLength.release();
        // declared length larger than the remaining bytes
        ByteBuf badLen = Unpooled.buffer();
        Varints.writeUvarint(badLen, ProtocolTags.TYPE);
        Varints.writeUvarint(badLen, 100);
        badLen.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(badLen));
        badLen.release();
        // FrameType id above Integer.MAX_VALUE
        ByteBuf badType = field(ProtocolTags.TYPE, 0x80000000L);
        try {
            assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(badType));
        } finally {
            badType.release();
        }
        // msgId above Integer.MAX_VALUE
        ByteBuf badMsg = field(ProtocolTags.MSG_ID, 0x80000000L);
        try {
            assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(badMsg));
        } finally {
            badMsg.release();
        }
    }

    @Test
    void decodeSkipsUnknownTagsAndRequiresTypeWithoutPayload() {
        ByteBuf input = Unpooled.buffer();
        // an unknown tag is skipped via the default branch
        input.writeBytes(varint(0xEE));
        input.writeBytes(varint(2));
        input.writeBytes(new byte[] {1, 2});
        input.writeBytes(field(ProtocolTags.TYPE, FrameType.PING.id()));
        try (Frame decoded = TlvFrameCodec.decode(input)) {
            assertEquals(FrameType.PING, decoded.type());
            assertTrue(decoded.payload() == null);
        }
        // no type and no payload -> the missing-type error path
        ByteBuf noType = Unpooled.buffer();
        noType.writeBytes(field(ProtocolTags.FLAGS, 1));
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(noType));
        noType.release();
        // a payload without a type is released before the missing-type error
        ByteBuf orphanPayload = Unpooled.buffer();
        Varints.writeUvarint(orphanPayload, ProtocolTags.PAYLOAD);
        Varints.writeUvarint(orphanPayload, 1);
        orphanPayload.writeByte(9);
        assertThrows(IllegalArgumentException.class, () -> TlvFrameCodec.decode(orphanPayload));
        orphanPayload.release();
    }

    @Test
    void encodeDecodesPushIdAndMultiByteValues() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] {1, 2});
        Frame frame = new Frame(FrameType.REQ, 3, 0, 300, 0, 0, 0, payload.retainedDuplicate());
        ByteBuf out = TlvFrameCodec.encode(io.netty.buffer.UnpooledByteBufAllocator.DEFAULT, frame);
        try (frame) {
            try (Frame decoded = TlvFrameCodec.decode(out.duplicate())) {
                assertEquals(FrameType.REQ, decoded.type());
                assertEquals(3, decoded.msgId());
                assertEquals(300, decoded.pushId());
                assertEquals(2, decoded.payload().readableBytes());
            }
        } finally {
            out.release();
            payload.release();
        }
    }

    @Test
    void encodeReleasesBufferWhenWriteFailsInsideTry() {
        // a zero-capacity buffer makes every write throw inside the try block
        ByteBufAllocator capped = new AbstractByteBufAllocator() {
            @Override
            public boolean isDirectBufferPooled() {
                return false;
            }

            @Override
            protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
                return Unpooled.buffer(0, 0);
            }

            @Override
            protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
                return Unpooled.buffer(0, 0);
            }
        };
        try (Frame frame = new Frame(FrameType.REQ, 1, 0, 0, 0, 0, 0, Unpooled.EMPTY_BUFFER.retainedDuplicate())) {
            assertThrows(RuntimeException.class, () -> TlvFrameCodec.encode(capped, frame));
        }
    }

    @Test
    void varintsRejectMalformedAndTruncatedValues() {
        // ten continuation bytes overflow the 64-bit single-arg reader
        ByteBuf ten = Unpooled.buffer();
        for (int i = 0; i < 10; i++) {
            ten.writeByte(0x80);
        }
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(ten.duplicate()));
        ten.release();
        // a non-positive length is rejected
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(Unpooled.EMPTY_BUFFER, 0));
        // a length larger than the remaining bytes underflows
        ByteBuf underflow = Unpooled.wrappedBuffer(new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(underflow, 5));
        // all-continuation bytes exhaust the bounded reader
        ByteBuf malformed = Unpooled.wrappedBuffer(new byte[] {(byte) 0x80, (byte) 0x80});
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(malformed, 2));
    }

    private static ByteBuf field(int tag, long value) {
        ByteBuf out = Unpooled.buffer();
        Varints.writeUvarint(out, tag);
        Varints.writeUvarint(out, Varints.uvarintSize(value));
        Varints.writeUvarint(out, value);
        return out;
    }

    private static byte[] varint(long value) {
        ByteBuf out = Unpooled.buffer();
        Varints.writeUvarint(out, value);
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        out.release();
        return bytes;
    }
}
