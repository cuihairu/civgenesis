package io.github.cuihairu.civgenesis.spring.observability;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static io.netty.handler.codec.http.HttpMethod.GET;

public final class PrometheusHttpServer implements Closeable {
    private final String host;
    private final int port;
    private final String path;
    private final PrometheusMeterRegistry registry;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public PrometheusHttpServer(String host, int port, String path, PrometheusMeterRegistry registry) {
        this.host = Objects.requireNonNullElse(host, "0.0.0.0");
        this.port = port;
        this.path = Objects.requireNonNullElse(path, "/metrics");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public synchronized void start() throws InterruptedException {
        if (channel != null) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new Initializer(path, registry));
        channel = bootstrap.bind(host, port).sync().channel();
    }

    public synchronized void stop() {
        if (channel == null) {
            return;
        }
        channel.close();
        channel = null;
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        bossGroup = null;
        workerGroup = null;
    }

    @Override
    public void close() {
        stop();
    }

    private static final class Initializer extends io.netty.channel.ChannelInitializer<SocketChannel> {
        private final String path;
        private final PrometheusMeterRegistry registry;

        private Initializer(String path, PrometheusMeterRegistry registry) {
            this.path = path;
            this.registry = registry;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new HttpServerCodec());
            ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
            ch.pipeline().addLast(new PrometheusHandler(path, registry));
        }
    }

    private static final class PrometheusHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final String path;
        private final PrometheusMeterRegistry registry;

        private PrometheusHandler(String path, PrometheusMeterRegistry registry) {
            this.path = path;
            this.registry = registry;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            if (req.method() != GET) {
                write(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "method not allowed", req);
                return;
            }
            if (!path.equals(req.uri())) {
                write(ctx, HttpResponseStatus.NOT_FOUND, "not found", req);
                return;
            }

            String body = registry.scrape();
            ByteBuf content = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8");
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            boolean keepAlive = HttpUtil.isKeepAlive(req);
            if (keepAlive) {
                resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
                ctx.writeAndFlush(resp);
            } else {
                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            }
        }

        private static void write(ChannelHandlerContext ctx, HttpResponseStatus status, String msg, FullHttpRequest req) {
            ByteBuf content = Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8);
            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            boolean keepAlive = HttpUtil.isKeepAlive(req);
            if (keepAlive) {
                resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
                ctx.writeAndFlush(resp);
            } else {
                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
