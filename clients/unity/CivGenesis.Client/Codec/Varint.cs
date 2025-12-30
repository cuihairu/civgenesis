using System;

namespace CivGenesis.Client.Codec
{
    public static class Varint
    {
        public static int SizeUVarint(ulong v)
        {
            int n = 1;
            ulong x = v;
            while ((x & ~0x7Ful) != 0)
            {
                x >>= 7;
                n++;
            }
            return n;
        }

        public static int WriteUVarint(ulong v, byte[] buffer, int offset)
        {
            int i = 0;
            ulong x = v;
            while ((x & ~0x7Ful) != 0)
            {
                buffer[offset + i] = (byte)((x & 0x7Ful) | 0x80u);
                x >>= 7;
                i++;
            }
            buffer[offset + i] = (byte)x;
            return i + 1;
        }

        public static ulong ReadUVarint(byte[] buffer, ref int offset)
        {
            ulong x = 0;
            int s = 0;
            for (int i = 0; i < 10; i++)
            {
                if (offset >= buffer.Length) throw new ArgumentException("Buffer underflow while reading uvarint");
                byte b = buffer[offset++];
                if ((b & 0x80) == 0)
                {
                    if (i == 9 && (b & 0xFE) != 0) throw new ArgumentException("uvarint overflows 64-bit");
                    return x | ((ulong)b << s);
                }
                x |= (ulong)(b & 0x7F) << s;
                s += 7;
            }
            throw new ArgumentException("Malformed uvarint");
        }

        public static ulong ReadUVarintValue(byte[] buffer, ref int offset, int length)
        {
            if (length <= 0) throw new ArgumentException("length must be > 0");
            int end = offset + length;
            if (end > buffer.Length) throw new ArgumentException("Buffer underflow while reading uvarint value");
            ulong x = 0;
            int s = 0;
            int i = 0;
            while (offset < end && i < 10)
            {
                byte b = buffer[offset++];
                i++;
                if ((b & 0x80) == 0)
                {
                    x |= (ulong)b << s;
                    if (offset != end) throw new ArgumentException("uvarint value length mismatch");
                    return x;
                }
                x |= (ulong)(b & 0x7F) << s;
                s += 7;
            }
            throw new ArgumentException("Malformed uvarint value");
        }
    }
}

