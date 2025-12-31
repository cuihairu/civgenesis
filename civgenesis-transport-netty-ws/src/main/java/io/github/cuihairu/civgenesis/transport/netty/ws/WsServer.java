package io.github.cuihairu.civgenesis.transport.netty.ws;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.Closeable;
import java.util.Objects;

public final class WsServer implements Closeable {
    private final WsServerConfig config;
    private final Dispatcher dispatcher;
    private final CivMetrics metrics;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public WsServer(WsServerConfig config, Dispatcher dispatcher) {
        this(config, dispatcher, CivMetrics.noop());
    }

    public WsServer(WsServerConfig config, Dispatcher dispatcher, CivMetrics metrics) {
        this.config = Objects.requireNonNullElse(config, WsServerConfig.defaults());
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.metrics = Objects.requireNonNullElse(metrics, CivMetrics.noop());
    }

    public synchronized void start() throws InterruptedException {
        if (channel != null) {
            return;
        }
        bossGroup = new NioEventLoopGroup(Math.max(1, config.bossThreads()));
        workerGroup = config.workerThreads() > 0 ? new NioEventLoopGroup(config.workerThreads()) : new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, Math.max(1, config.soBacklog()))
                .childOption(ChannelOption.ALLOCATOR, config.pooledAllocator() ? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT)
                .childHandler(new WsServerInitializer(config, dispatcher, metrics));
        if (config.recvBufBytes() > 0) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, config.recvBufBytes());
        }
        if (config.sendBufBytes() > 0) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, config.sendBufBytes());
        }
        channel = bootstrap.bind(config.port()).sync().channel();
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

    private static final class WsServerInitializer extends io.netty.channel.ChannelInitializer<io.netty.channel.socket.SocketChannel> {
        private final WsServerConfig config;
        private final Dispatcher dispatcher;
        private final CivMetrics metrics;

        private WsServerInitializer(WsServerConfig config, Dispatcher dispatcher, CivMetrics metrics) {
            this.config = config;
            this.dispatcher = dispatcher;
            this.metrics = metrics;
        }

        @Override
        protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
            ch.pipeline().addLast(new HttpServerCodec());
            ch.pipeline().addLast(new HttpObjectAggregator(65536));
            ch.pipeline().addLast(new WebSocketServerProtocolHandler(config.path(), null, true, config.maxFrameBytes()));
            ch.pipeline().addLast(new IdleStateHandler(config.idleTimeoutSeconds(), 0, 0));
            ch.pipeline().addLast(new PingBeforeCloseHandler(config.pingBeforeClose(), config.pingTimeoutMillis()));
            ch.pipeline().addLast(new NettyWsFrameDecoder(metrics));
            ch.pipeline().addLast(new NettyWsFrameEncoder(metrics));
            ch.pipeline().addLast(new DispatcherChannelHandler(dispatcher, metrics));
        }
    }
}
