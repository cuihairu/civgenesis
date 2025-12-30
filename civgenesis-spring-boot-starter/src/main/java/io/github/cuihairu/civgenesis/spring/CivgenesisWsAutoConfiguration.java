package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.github.cuihairu.civgenesis.transport.netty.ws.WsServer;
import io.github.cuihairu.civgenesis.transport.netty.ws.WsServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisWsProperties.class)
@ConditionalOnProperty(prefix = "civgenesis.ws", name = "enabled", havingValue = "true")
public class CivgenesisWsAutoConfiguration {
    @Bean
    @ConditionalOnBean(Dispatcher.class)
    public WsServer civgenesisWsServer(CivgenesisWsProperties props, Dispatcher dispatcher, CivMetrics metrics) {
        WsServerConfig config = new WsServerConfig(
                props.getPort(),
                props.getPath(),
                props.getMaxFrameBytes(),
                props.getIdleTimeoutSeconds(),
                props.isPingBeforeClose(),
                props.getPingTimeoutMillis()
        );
        return new WsServer(config, dispatcher, metrics);
    }

    @Bean
    @ConditionalOnBean(WsServer.class)
    public SmartLifecycle civgenesisWsServerLifecycle(WsServer server) {
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
