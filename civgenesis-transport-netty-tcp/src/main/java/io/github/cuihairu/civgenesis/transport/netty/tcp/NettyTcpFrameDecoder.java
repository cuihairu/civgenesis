package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

final class NettyTcpFrameDecoder extends SimpleChannelInboundHandler<ByteBuf> {
    private final CivMetrics metrics;

    NettyTcpFrameDecoder(CivMetrics metrics) {
        this.metrics = metrics == null ? CivMetrics.noop() : metrics;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        int bytes = msg.readableBytes();
        Frame frame = TlvFrameCodec.decode(msg.duplicate());
        metrics.onFrameIn("tcp", frame.type(), frame.msgId(), bytes);
        ctx.fireChannelRead(frame);
    }
}

