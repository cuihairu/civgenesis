using System;

namespace CivGenesis.Client.Transport
{
    public interface ITransport : IDisposable
    {
        bool IsConnected { get; }

        event Action<byte[]> OnBinaryMessage;
        event Action OnOpen;
        event Action<string> OnClose;
        event Action<Exception> OnError;

        void Connect(string url);
        void SendBinary(byte[] data);
        void Close();
    }
}

