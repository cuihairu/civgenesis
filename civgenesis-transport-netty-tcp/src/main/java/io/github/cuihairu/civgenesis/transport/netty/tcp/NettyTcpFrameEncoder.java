package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

final class NettyTcpFrameEncoder extends MessageToMessageEncoder<Frame> {
    private final CivMetrics metrics;

    NettyTcpFrameEncoder(CivMetrics metrics) {
        this.metrics = metrics == null ? CivMetrics.noop() : metrics;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame msg, List<Object> out) {
        ByteBuf encoded = TlvFrameCodec.encode(ctx.alloc(), msg);
        try {
            metrics.onFrameOut("tcp", msg.type(), msg.msgId(), encoded.readableBytes());
            out.add(encoded);
        } catch (RuntimeException e) {
            encoded.release();
            throw e;
        } finally {
            msg.close();
        }
    }
}

