package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.github.cuihairu.civgenesis.codec.tlv.TlvFrameCodec;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.List;

final class NettyWsFrameDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {
    private final CivMetrics metrics;

    NettyWsFrameDecoder(CivMetrics metrics) {
        this.metrics = metrics == null ? CivMetrics.noop() : metrics;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame msg, List<Object> out) {
        int bytes = msg.content().readableBytes();
        Frame frame = TlvFrameCodec.decode(msg.content().duplicate());
        metrics.onFrameIn("ws", frame.type(), frame.msgId(), bytes);
        out.add(frame);
    }
}
