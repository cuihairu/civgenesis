package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisObservabilityProperties.class)
public class CivgenesisObservabilityAutoConfiguration {
    @Bean
    @Conditional(RegistryCondition.class)
    public PrometheusMeterRegistry civgenesisPrometheusRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Bean
    @Primary
    @Conditional(MetricsImplCondition.class)
    public CivMetrics civgenesisMicrometerCivMetrics(PrometheusMeterRegistry registry) {
        return new MicrometerCivMetrics(registry);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "civgenesis.observability.tracing", name = "enabled", havingValue = "true")
    public CivTracer civgenesisOpenTelemetryCivTracer(CivgenesisObservabilityProperties props) {
        return new OpenTelemetryCivTracer(props.getTracing().getInstrumentationName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "civgenesis.observability.prometheus", name = "enabled", havingValue = "true")
    public PrometheusHttpServer civgenesisPrometheusHttpServer(CivgenesisObservabilityProperties props, PrometheusMeterRegistry registry) {
        var p = props.getPrometheus();
        return new PrometheusHttpServer(p.getHost(), p.getPort(), p.getPath(), registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "civgenesis.observability.prometheus", name = "enabled", havingValue = "true")
    public SmartLifecycle civgenesisPrometheusHttpServerLifecycle(PrometheusHttpServer server) {
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
                return Integer.MAX_VALUE - 10;
            }
        };
    }

    static final class RegistryCondition extends AnyNestedCondition {
        RegistryCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(prefix = "civgenesis.observability.metrics", name = "enabled", havingValue = "true")
        static class MetricsEnabled {}

        @ConditionalOnProperty(prefix = "civgenesis.observability.prometheus", name = "enabled", havingValue = "true")
        static class PrometheusEnabled {}
    }

    static final class MetricsImplCondition extends AnyNestedCondition {
        MetricsImplCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(prefix = "civgenesis.observability.metrics", name = "enabled", havingValue = "true")
        static class MetricsEnabled {}

        @ConditionalOnProperty(prefix = "civgenesis.observability.prometheus", name = "enabled", havingValue = "true")
        static class PrometheusEnabled {}
    }
}
