namespace CivGenesis.Client.Protocol
{
    public enum FrameType : uint
    {
        Req = 1,
        Resp = 2,
        Push = 3,
        Ack = 4,
        Ping = 5,
        Pong = 6,
    }
}

