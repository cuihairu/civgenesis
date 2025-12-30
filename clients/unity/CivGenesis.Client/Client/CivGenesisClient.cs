using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using CivGenesis.Client.Codec;
using CivGenesis.Client.Protocol;
using CivGenesis.Client.Transport;

namespace CivGenesis.Client
{
    public sealed class CivGenesisClient : IDisposable
    {
        private readonly ITransport _transport;
        private long _seq = 1;
        private ulong _lastAppliedPushId = 0;
        private readonly Dictionary<ulong, TaskCompletionSource<Frame>> _pending = new Dictionary<ulong, TaskCompletionSource<Frame>>();

        public CivGenesisClient(ITransport transport)
        {
            _transport = transport ?? throw new ArgumentNullException(nameof(transport));
            _transport.OnBinaryMessage += OnBinary;
        }

        public ulong LastAppliedPushId => _lastAppliedPushId;

        public Task<Frame> RequestAsync(uint msgId, byte[] payload, TimeSpan timeout)
        {
            ulong seq = (ulong)Interlocked.Increment(ref _seq);
            var tcs = new TaskCompletionSource<Frame>(TaskCreationOptions.RunContinuationsAsynchronously);
            lock (_pending) _pending[seq] = tcs;

            var frame = new Frame
            {
                Type = FrameType.Req,
                MsgId = msgId,
                Seq = seq,
                Payload = payload ?? Array.Empty<byte>(),
            };
            _transport.SendBinary(TlvFrameCodec.Encode(frame));

            _ = TimeoutTask(seq, timeout);
            return tcs.Task;
        }

        public void SendAck(ulong pushId)
        {
            var frame = new Frame
            {
                Type = FrameType.Ack,
                PushId = pushId,
                Payload = Array.Empty<byte>(),
            };
            _transport.SendBinary(TlvFrameCodec.Encode(frame));
        }

        private async Task TimeoutTask(ulong seq, TimeSpan timeout)
        {
            await Task.Delay(timeout).ConfigureAwait(false);
            TaskCompletionSource<Frame>? tcs = null;
            lock (_pending)
            {
                if (_pending.TryGetValue(seq, out tcs))
                {
                    _pending.Remove(seq);
                }
            }
            tcs?.TrySetException(new TimeoutException("request timeout"));
        }

        private void OnBinary(byte[] data)
        {
            var frame = TlvFrameCodec.Decode(data);
            if (frame.Type == FrameType.Resp && frame.Seq > 0)
            {
                TaskCompletionSource<Frame>? tcs = null;
                lock (_pending)
                {
                    if (_pending.TryGetValue(frame.Seq, out tcs))
                    {
                        _pending.Remove(frame.Seq);
                    }
                }
                tcs?.TrySetResult(frame);
                return;
            }

            if (frame.Type == FrameType.Push && frame.PushId > 0)
            {
                if (frame.PushId <= _lastAppliedPushId)
                {
                    if (frame.HasFlag(ProtocolFlags.AckRequired))
                    {
                        SendAck(_lastAppliedPushId);
                    }
                    return;
                }
                _lastAppliedPushId = frame.PushId;
                if (frame.HasFlag(ProtocolFlags.AckRequired))
                {
                    SendAck(_lastAppliedPushId);
                }
                return;
            }
        }

        public void Dispose()
        {
            _transport.OnBinaryMessage -= OnBinary;
            _transport.Dispose();
        }
    }
}

