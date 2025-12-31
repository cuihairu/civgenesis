package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.github.cuihairu.civgenesis.transport.netty.tcp.TcpServer;
import io.github.cuihairu.civgenesis.transport.netty.tcp.TcpServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisTcpProperties.class)
@ConditionalOnProperty(prefix = "civgenesis.tcp", name = "enabled", havingValue = "true")
public class CivgenesisTcpAutoConfiguration {
    @Bean
    @ConditionalOnBean(Dispatcher.class)
    public TcpServer civgenesisTcpServer(CivgenesisTcpProperties props, Dispatcher dispatcher, CivMetrics metrics) {
        TcpServerConfig config = new TcpServerConfig(
                props.getBossThreads(),
                props.getWorkerThreads(),
                props.getHost(),
                props.getPort(),
                props.getSoBacklog(),
                props.getRecvBufBytes(),
                props.getSendBufBytes(),
                props.isPooledAllocator(),
                props.getMaxFrameBytes(),
                props.getIdleTimeoutSeconds()
        );
        return new TcpServer(config, dispatcher, metrics);
    }

    @Bean
    @ConditionalOnBean(TcpServer.class)
    public SmartLifecycle civgenesisTcpServerLifecycle(TcpServer server) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                try {
                    server.start();
                    running = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void stop() {
                running = false;
                server.stop();
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE;
            }
        };
    }
}

