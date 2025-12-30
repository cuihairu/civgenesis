using System;

namespace CivGenesis.Client.Protocol
{
    public sealed class Frame
    {
        public FrameType Type { get; set; }
        public uint MsgId { get; set; }
        public ulong Seq { get; set; }
        public ulong PushId { get; set; }
        public ulong Flags { get; set; }
        public ulong Ts { get; set; }
        public ulong AckPushId { get; set; }
        public byte[] Payload { get; set; } = Array.Empty<byte>();

        public bool HasFlag(ulong flag) => (Flags & flag) != 0;
    }
}

