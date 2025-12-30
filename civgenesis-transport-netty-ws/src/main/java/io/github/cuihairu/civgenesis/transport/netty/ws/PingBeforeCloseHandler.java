package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class PingBeforeCloseHandler extends ChannelInboundHandlerAdapter {
    private final boolean enabled;
    private final int pingTimeoutMillis;
    private volatile ScheduledFuture<?> pendingClose;

    PingBeforeCloseHandler(boolean enabled, int pingTimeoutMillis) {
        this.enabled = enabled;
        this.pingTimeoutMillis = pingTimeoutMillis;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!(evt instanceof IdleStateEvent e) || e.state() != IdleState.READER_IDLE) {
            super.userEventTriggered(ctx, evt);
            return;
        }
        if (!enabled) {
            ctx.close();
            return;
        }
        ctx.writeAndFlush(new PingWebSocketFrame());
        ScheduledFuture<?> old = pendingClose;
        if (old != null) {
            old.cancel(false);
        }
        pendingClose = ctx.executor().schedule((Runnable) ctx::close, pingTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PongWebSocketFrame) {
            ScheduledFuture<?> old = pendingClose;
            if (old != null) {
                old.cancel(false);
                pendingClose = null;
            }
        }
        super.channelRead(ctx, msg);
    }
}
