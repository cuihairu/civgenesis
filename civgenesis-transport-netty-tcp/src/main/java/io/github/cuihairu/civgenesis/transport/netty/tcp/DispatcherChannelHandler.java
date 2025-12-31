package io.github.cuihairu.civgenesis.transport.netty.tcp;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.transport.Connection;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;

import java.util.concurrent.atomic.AtomicLong;

final class DispatcherChannelHandler extends SimpleChannelInboundHandler<Frame> {
    private static final AttributeKey<Connection> CONN = AttributeKey.valueOf("civgenesis.conn");
    private static final AtomicLong IDS = new AtomicLong(1);

    private final Dispatcher dispatcher;
    private final CivMetrics metrics;

    DispatcherChannelHandler(Dispatcher dispatcher, CivMetrics metrics) {
        this.dispatcher = dispatcher;
        this.metrics = metrics == null ? CivMetrics.noop() : metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        long id = IDS.getAndIncrement();
        Connection conn = new NettyTcpConnection(id, ctx.channel());
        ctx.channel().attr(CONN).set(conn);
        metrics.onConnectionOpen("tcp");
        dispatcher.onConnect(conn);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Connection conn = ctx.channel().attr(CONN).getAndSet(null);
        if (conn != null) {
            dispatcher.onDisconnect(conn);
            metrics.onConnectionClose("tcp");
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Frame msg) {
        Connection conn = ctx.channel().attr(CONN).get();
        if (conn == null) {
            msg.close();
            return;
        }
        try {
            dispatcher.handle(conn, msg);
        } finally {
            msg.close();
        }
    }
}

