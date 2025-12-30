package io.github.cuihairu.civgenesis.codec.tlv;

import io.netty.buffer.ByteBuf;

public final class Varints {
    private Varints() {}

    public static long readUvarint(ByteBuf in) {
        long x = 0;
        int s = 0;
        for (int i = 0; i < 10; i++) {
            if (!in.isReadable()) {
                throw new IllegalArgumentException("Buffer underflow while reading uvarint");
            }
            int b = in.readByte() & 0xFF;
            if ((b & 0x80) == 0) {
                if (i == 9 && (b & 0xFE) != 0) {
                    throw new IllegalArgumentException("uvarint overflows 64-bit");
                }
                return x | ((long) b << s);
            }
            x |= (long) (b & 0x7F) << s;
            s += 7;
        }
        throw new IllegalArgumentException("Malformed uvarint");
    }

    public static long readUvarint(ByteBuf in, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        int start = in.readerIndex();
        int end = start + length;
        if (end > in.writerIndex()) {
            throw new IllegalArgumentException("Buffer underflow while reading uvarint value");
        }
        long x = 0;
        int s = 0;
        int i = 0;
        while (in.readerIndex() < end && i < 10) {
            int b = in.readByte() & 0xFF;
            i++;
            if ((b & 0x80) == 0) {
                x |= ((long) b << s);
                if (in.readerIndex() != end) {
                    throw new IllegalArgumentException("uvarint value length mismatch");
                }
                return x;
            }
            x |= (long) (b & 0x7F) << s;
            s += 7;
        }
        throw new IllegalArgumentException("Malformed uvarint value");
    }

    public static int writeUvarint(ByteBuf out, long v) {
        int written = 0;
        long x = v;
        while ((x & ~0x7FL) != 0) {
            out.writeByte((int) ((x & 0x7F) | 0x80));
            x >>>= 7;
            written++;
        }
        out.writeByte((int) x);
        return written + 1;
    }

    public static int uvarintSize(long v) {
        int n = 1;
        long x = v;
        while ((x & ~0x7FL) != 0) {
            x >>>= 7;
            n++;
        }
        return n;
    }
}

