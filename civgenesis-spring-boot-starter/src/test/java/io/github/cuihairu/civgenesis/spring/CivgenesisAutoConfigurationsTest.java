package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.github.cuihairu.civgenesis.dispatcher.runtime.DispatcherRuntime;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.jobs.CivJob;
import io.github.cuihairu.civgenesis.jobs.JobExecutionContext;
import io.github.cuihairu.civgenesis.jobs.JobRunner;
import io.github.cuihairu.civgenesis.jobs.JobScheduler;
import io.github.cuihairu.civgenesis.spring.jobs.CivgenesisJobsAutoConfiguration;
import io.github.cuihairu.civgenesis.spring.observability.CivgenesisObservabilityAutoConfiguration;
import io.github.cuihairu.civgenesis.spring.observability.CivgenesisObservabilityProperties;
import io.github.cuihairu.civgenesis.spring.observability.PrometheusHttpServer;
import io.github.cuihairu.civgenesis.spring.registry.CivgenesisNacosProperties;
import io.github.cuihairu.civgenesis.spring.registry.CivgenesisRegistryAutoConfiguration;
import io.github.cuihairu.civgenesis.spring.system.CivgenesisSystemAutoConfiguration;
import io.github.cuihairu.civgenesis.system.SystemServerConfig;
import io.github.cuihairu.civgenesis.system.SystemServerEpoch;
import io.github.cuihairu.civgenesis.system.auth.DenyAllTokenAuthenticator;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;
import io.github.cuihairu.civgenesis.system.controller.SystemClientHelloController;
import io.github.cuihairu.civgenesis.system.controller.SystemResumeController;
import io.github.cuihairu.civgenesis.system.controller.SystemSyncController;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import io.github.cuihairu.civgenesis.system.snapshot.UnsupportedSnapshotProvider;
import io.github.cuihairu.civgenesis.transport.netty.tcp.TcpServer;
import io.github.cuihairu.civgenesis.transport.netty.ws.WsServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivgenesisAutoConfigurationsTest {
    private static final int DEMO_MSG_ID = 4001;

    @Test
    void dispatcherAutoConfigurationBuildsRuntimeAndScansControllers() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2")
                .withUserConfiguration(DemoControllerConfig.class)
                .run(ctx -> {
                    Dispatcher dispatcher = ctx.getBean(Dispatcher.class);
                    assertTrue(dispatcher instanceof DispatcherRuntime);
                    assertNotNull(ctx.getBean(ShardExecutor.class));
                    assertNotNull(ctx.getBean(PayloadCodec.class));
                    assertNotNull(ctx.getBean(CivMetrics.class));
                    assertNotNull(ctx.getBean(CivTracer.class));
                    RouteTable routeTable = ctx.getBean(RouteTable.class);
                    assertNotNull(routeTable.get(DEMO_MSG_ID));
                    assertEquals(2, ctx.getBean(CivgenesisDispatcherProperties.class).getShards());
                });
    }

    @Test
    void dispatcherAutoConfigurationCanBeDisabledAndBeansOverridden() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.enabled=false")
                .run(ctx -> assertTrue(ctx.getBeansOfType(Dispatcher.class).isEmpty()));

        CivMetrics customMetrics = CivMetrics.noop();
        PayloadCodec customCodec = new io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2")
                .withBean("customMetrics", CivMetrics.class, () -> customMetrics)
                .withBean("customCodec", PayloadCodec.class, () -> customCodec)
                .run(ctx -> {
                    assertSame(customMetrics, ctx.getBean(CivMetrics.class));
                    assertSame(customCodec, ctx.getBean(PayloadCodec.class));
                });
    }

    @Test
    void tcpAutoConfigurationStartsAndStopsServer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class, CivgenesisTcpAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2", "civgenesis.tcp.enabled=true", "civgenesis.tcp.port=0")
                .run(ctx -> {
                    TcpServer server = ctx.getBean(TcpServer.class);
                    assertNotNull(server);
                    assertEquals(0, ctx.getBean(CivgenesisTcpProperties.class).getPort());
                });
    }

    @Test
    void tcpAutoConfigurationDisabledWithoutDispatcherOrFlag() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisTcpAutoConfiguration.class))
                .run(ctx -> assertTrue(ctx.getBeansOfType(TcpServer.class).isEmpty()));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class, CivgenesisTcpAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2")
                .run(ctx -> assertTrue(ctx.getBeansOfType(TcpServer.class).isEmpty()));
    }

    @Test
    void wsAutoConfigurationStartsAndStopsServer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class, CivgenesisWsAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2", "civgenesis.ws.enabled=true", "civgenesis.ws.port=0")
                .run(ctx -> assertNotNull(ctx.getBean(WsServer.class)));
    }

    @Test
    void wsAutoConfigurationDisabledByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisWsAutoConfiguration.class))
                .run(ctx -> assertTrue(ctx.getBeansOfType(WsServer.class).isEmpty()));
    }

    @Test
    void jobsAutoConfigurationSchedulesDeclaredJobs() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisDispatcherAutoConfiguration.class, CivgenesisJobsAutoConfiguration.class))
                .withUserConfiguration(DemoJobConfig.class)
                .withPropertyValues("civgenesis.dispatcher.shards=2", "civgenesis.jobs.enabled=true")
                .run(ctx -> {
                    assertNotNull(ctx.getBean(JobScheduler.class));
                    JobRunner runner = ctx.getBean(JobRunner.class);
                    assertNotNull(runner);
                    Thread.sleep(200);
                    assertTrue(DemoJobConfig.runs.get() >= 1);
                });

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisJobsAutoConfiguration.class))
                .run(ctx -> assertTrue(ctx.getBeansOfType(JobRunner.class).isEmpty()));
    }

    @Test
    void registryAutoConfigurationBacksOffForCustomRegistry() {
        io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry custom =
                org.mockito.Mockito.mock(io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisRegistryAutoConfiguration.class))
                .withBean("customRegistry", io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry.class, () -> custom)
                .withPropertyValues("civgenesis.registry.nacos.group=CG", "civgenesis.registry.nacos.namespace=ns")
                .run(ctx -> {
                    CivgenesisNacosProperties props = ctx.getBean(CivgenesisNacosProperties.class);
                    assertEquals("CG", props.getGroup());
                    assertEquals("ns", props.getNamespace());
                    assertSame(custom, ctx.getBean(io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry.class));
                });
    }

    @Test
    void registryAutoConfigurationCreatesRegistryWithBoundProperties() throws Exception {
        CivgenesisNacosProperties props = new CivgenesisNacosProperties();
        props.setServerAddr("127.0.0.1:8848");
        props.setNamespace("");
        props.setUsername("");
        props.setPassword("");
        props.setGroup("CG");
        io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry registry =
                new CivgenesisRegistryAutoConfiguration().civgenesisNacosServiceRegistry(props);
        assertNotNull(registry.namingService());
        registry.close();
    }

    @Test
    void systemAutoConfigurationWiresControllersAndDefaults() {
        TokenAuthenticator custom = new DenyAllTokenAuthenticator();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CivgenesisDispatcherAutoConfiguration.class,
                        CivgenesisWsAutoConfiguration.class,
                        CivgenesisSystemAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2", "civgenesis.ws.enabled=true", "civgenesis.ws.port=0", "civgenesis.system.gzip-enabled=true")
                .withBean("customAuth", TokenAuthenticator.class, () -> custom)
                .run(ctx -> {
                    assertNotNull(ctx.getBean(SystemServerEpoch.class));
                    SystemServerConfig config = ctx.getBean(SystemServerConfig.class);
                    assertTrue(config.gzipEnabled());
                    assertSame(custom, ctx.getBean(TokenAuthenticator.class));
                    SnapshotProvider provider = ctx.getBean(SnapshotProvider.class);
                    assertTrue(provider instanceof UnsupportedSnapshotProvider);
                    assertNotNull(ctx.getBean(SystemClientHelloController.class));
                    assertNotNull(ctx.getBean(SystemResumeController.class));
                    assertNotNull(ctx.getBean(SystemSyncController.class));
                });
    }

    @Test
    void observabilityAutoConfigurationCreatesMetricsTracingAndHttpServer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisObservabilityAutoConfiguration.class))
                .withPropertyValues(
                        "civgenesis.observability.metrics.enabled=true",
                        "civgenesis.observability.tracing.enabled=true",
                        "civgenesis.observability.prometheus.enabled=true",
                        "civgenesis.observability.prometheus.port=0")
                .run(ctx -> {
                    assertNotNull(ctx.getBean(PrometheusMeterRegistry.class));
                    assertTrue(ctx.containsBean("civgenesisMicrometerCivMetrics"));
                    assertTrue(ctx.containsBean("civgenesisOpenTelemetryCivTracer"));
                    PrometheusHttpServer server = ctx.getBean(PrometheusHttpServer.class);
                    assertNotNull(server);
                    CivgenesisObservabilityProperties props = ctx.getBean(CivgenesisObservabilityProperties.class);
                    assertTrue(props.getMetrics().isEnabled());
                });
    }

    @Test
    void observabilityAutoConfigurationGatesTracingAndHttpServerBehindFlags() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CivgenesisObservabilityAutoConfiguration.class))
                .run(ctx -> {
                    assertTrue(ctx.getBeansOfType(CivTracer.class).isEmpty());
                    assertTrue(ctx.getBeansOfType(PrometheusHttpServer.class).isEmpty());
                });
    }

    @Test
    void serverLifecyclesWrapInterruptionDuringStart() throws Exception {
        Thread.currentThread().interrupt();
        try {
            TcpServer tcp = org.mockito.Mockito.mock(TcpServer.class);
            org.mockito.Mockito.doThrow(new InterruptedException("stop")).when(tcp).start();
            assertThrows(IllegalStateException.class,
                    () -> new CivgenesisTcpAutoConfiguration().civgenesisTcpServerLifecycle(tcp).start());

            WsServer ws = org.mockito.Mockito.mock(WsServer.class);
            org.mockito.Mockito.doThrow(new InterruptedException("stop")).when(ws).start();
            assertThrows(IllegalStateException.class,
                    () -> new CivgenesisWsAutoConfiguration().civgenesisWsServerLifecycle(ws).start());

            PrometheusHttpServer http = org.mockito.Mockito.mock(PrometheusHttpServer.class);
            org.mockito.Mockito.doThrow(new InterruptedException("stop")).when(http).start();
            assertThrows(IllegalStateException.class,
                    () -> new CivgenesisObservabilityAutoConfiguration().civgenesisPrometheusHttpServerLifecycle(http).start());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void systemAutoConfigurationProvidesDenyAllAuthenticatorByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CivgenesisDispatcherAutoConfiguration.class,
                        CivgenesisWsAutoConfiguration.class,
                        CivgenesisSystemAutoConfiguration.class))
                .withPropertyValues("civgenesis.dispatcher.shards=2", "civgenesis.ws.enabled=true", "civgenesis.ws.port=0")
                .run(ctx -> assertTrue(ctx.getBean(TokenAuthenticator.class) instanceof DenyAllTokenAuthenticator));
    }

    @Configuration(proxyBeanMethods = false)
    static class DemoControllerConfig {
        @Bean
        DemoController demoController() {
            return new DemoController();
        }
    }

    @GameController
    public static class DemoController {
        @GameRoute(id = DEMO_MSG_ID, open = true)
        public void ping(RequestContext ctx, io.github.cuihairu.civgenesis.protocol.system.Error req) {
            ctx.reply(io.github.cuihairu.civgenesis.protocol.system.Error.getDefaultInstance());
        }
    }

    static class DemoJobConfig {
        static final AtomicInteger runs = new AtomicInteger();

        @Bean
        CivJob demoJob() {
            return new CivJob() {
                @Override
                public String name() {
                    return "demo";
                }

                @Override
                public Duration fixedDelay() {
                    return Duration.ofMillis(50);
                }

                @Override
                public void run(JobExecutionContext ctx) {
                    runs.incrementAndGet();
                }
            };
        }
    }
}
