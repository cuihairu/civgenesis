namespace CivGenesis.Client.Protocol
{
    public static class ProtocolFlags
    {
        public const ulong Error = 0x01;
        public const ulong Compress = 0x02;
        public const ulong Encrypt = 0x04;
        public const ulong AckRequired = 0x08;
    }
}

