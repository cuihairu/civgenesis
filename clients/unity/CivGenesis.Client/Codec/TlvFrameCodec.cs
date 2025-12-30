using System;
using CivGenesis.Client.Protocol;

namespace CivGenesis.Client.Codec
{
    public static class TlvFrameCodec
    {
        public static byte[] Encode(Frame frame)
        {
            int size =
                FieldSizeUVarint(ProtocolTags.Type, (ulong)frame.Type) +
                (frame.Flags != 0 ? FieldSizeUVarint(ProtocolTags.Flags, frame.Flags) : 0) +
                (frame.MsgId != 0 ? FieldSizeUVarint(ProtocolTags.MsgId, frame.MsgId) : 0) +
                (frame.Seq != 0 ? FieldSizeUVarint(ProtocolTags.Seq, frame.Seq) : 0) +
                (frame.PushId != 0 ? FieldSizeUVarint(ProtocolTags.PushId, frame.PushId) : 0) +
                (frame.Ts != 0 ? FieldSizeUVarint(ProtocolTags.Ts, frame.Ts) : 0) +
                FieldSizeBytes(ProtocolTags.Payload, frame.Payload) +
                (frame.AckPushId != 0 ? FieldSizeUVarint(ProtocolTags.AckPushId, frame.AckPushId) : 0);

            byte[] buf = new byte[size];
            int off = 0;
            off += WriteUVarintField(buf, off, ProtocolTags.Type, (ulong)frame.Type);
            if (frame.Flags != 0) off += WriteUVarintField(buf, off, ProtocolTags.Flags, frame.Flags);
            if (frame.MsgId != 0) off += WriteUVarintField(buf, off, ProtocolTags.MsgId, frame.MsgId);
            if (frame.Seq != 0) off += WriteUVarintField(buf, off, ProtocolTags.Seq, frame.Seq);
            if (frame.PushId != 0) off += WriteUVarintField(buf, off, ProtocolTags.PushId, frame.PushId);
            if (frame.Ts != 0) off += WriteUVarintField(buf, off, ProtocolTags.Ts, frame.Ts);
            off += WriteBytesField(buf, off, ProtocolTags.Payload, frame.Payload);
            if (frame.AckPushId != 0) off += WriteUVarintField(buf, off, ProtocolTags.AckPushId, frame.AckPushId);
            return buf;
        }

        public static Frame Decode(byte[] buf)
        {
            var frame = new Frame();
            int off = 0;
            while (off < buf.Length)
            {
                ulong tag = Varint.ReadUVarint(buf, ref off);
                ulong lenU = Varint.ReadUVarint(buf, ref off);
                if (lenU > int.MaxValue) throw new ArgumentException("TLV length too large");
                int len = (int)lenU;
                if (off + len > buf.Length) throw new ArgumentException("Invalid TLV length");

                switch ((uint)tag)
                {
                    case ProtocolTags.Type:
                        frame.Type = (FrameType)Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.MsgId:
                        frame.MsgId = (uint)Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.Seq:
                        frame.Seq = Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.PushId:
                        frame.PushId = Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.Flags:
                        frame.Flags = Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.Ts:
                        frame.Ts = Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.AckPushId:
                        frame.AckPushId = Varint.ReadUVarintValue(buf, ref off, len);
                        break;
                    case ProtocolTags.Payload:
                        frame.Payload = new byte[len];
                        Buffer.BlockCopy(buf, off, frame.Payload, 0, len);
                        off += len;
                        break;
                    default:
                        off += len;
                        break;
                }
            }
            return frame;
        }

        private static int FieldSizeUVarint(uint tag, ulong value)
        {
            int vSize = Varint.SizeUVarint(value);
            return Varint.SizeUVarint(tag) + Varint.SizeUVarint((ulong)vSize) + vSize;
        }

        private static int FieldSizeBytes(uint tag, byte[] bytes)
        {
            int len = bytes?.Length ?? 0;
            return Varint.SizeUVarint(tag) + Varint.SizeUVarint((ulong)len) + len;
        }

        private static int WriteUVarintField(byte[] buf, int off, uint tag, ulong value)
        {
            int start = off;
            off += Varint.WriteUVarint(tag, buf, off);
            int vSize = Varint.SizeUVarint(value);
            off += Varint.WriteUVarint((ulong)vSize, buf, off);
            off += Varint.WriteUVarint(value, buf, off);
            return off - start;
        }

        private static int WriteBytesField(byte[] buf, int off, uint tag, byte[] bytes)
        {
            bytes ??= Array.Empty<byte>();
            int start = off;
            off += Varint.WriteUVarint(tag, buf, off);
            off += Varint.WriteUVarint((ulong)bytes.Length, buf, off);
            Buffer.BlockCopy(bytes, 0, buf, off, bytes.Length);
            off += bytes.Length;
            return off - start;
        }
    }
}

