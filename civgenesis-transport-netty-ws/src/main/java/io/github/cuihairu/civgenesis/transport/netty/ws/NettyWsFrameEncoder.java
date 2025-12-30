package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.List;

final class NettyWsFrameEncoder extends MessageToMessageEncoder<Frame> {
    private final CivMetrics metrics;

    NettyWsFrameEncoder(CivMetrics metrics) {
        this.metrics = metrics == null ? CivMetrics.noop() : metrics;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame msg, List<Object> out) {
        ByteBuf encoded = TlvFrameCodec.encode(ctx.alloc(), msg);
        try {
            metrics.onFrameOut("ws", msg.type(), msg.msgId(), encoded.readableBytes());
            out.add(new BinaryWebSocketFrame(encoded));
        } catch (RuntimeException e) {
            encoded.release();
            throw e;
        } finally {
            msg.close();
        }
    }
}
