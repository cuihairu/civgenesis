package io.github.cuihairu.civgenesis.codec.tlv;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarintsTest {
    @Test
    void roundTripUvarint() {
        long[] values = new long[]{
                0L, 1L, 2L, 127L, 128L, 255L, 300L, 16384L, 1L << 20, (1L << 31) - 1, (1L << 32), Long.MAX_VALUE
        };
        for (long v : values) {
            ByteBuf buf = Unpooled.buffer();
            try {
                int written = Varints.writeUvarint(buf, v);
                assertEquals(written, buf.readableBytes());
                long decoded = Varints.readUvarint(buf);
                assertEquals(v, decoded);
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void rejectsTruncatedAndOverflowingUvarints() {
        ByteBuf truncated = Unpooled.wrappedBuffer(new byte[] {(byte) 0x80});
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(truncated));

        ByteBuf tenBytes = Unpooled.wrappedBuffer(new byte[] {
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 2
        });
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(tenBytes));

        ByteBuf malformedLength = Unpooled.wrappedBuffer(new byte[] {1, (byte) 0x80});
        assertThrows(IllegalArgumentException.class, () -> Varints.readUvarint(malformedLength, 2));
    }
}

