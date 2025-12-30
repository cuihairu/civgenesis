package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.codec.PayloadCodec;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.github.cuihairu.civgenesis.dispatcher.runtime.Dispatcher;
import io.github.cuihairu.civgenesis.dispatcher.runtime.DispatcherConfig;
import io.github.cuihairu.civgenesis.dispatcher.runtime.DispatcherRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisDispatcherProperties.class)
@ConditionalOnProperty(prefix = "civgenesis.dispatcher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CivgenesisDispatcherAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CivMetrics civgenesisCivMetrics() {
        return CivMetrics.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public CivTracer civgenesisCivTracer() {
        return CivTracer.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public ShardExecutor civgenesisShardExecutor(CivgenesisDispatcherProperties props) {
        return new ShardExecutor(props.getShards(), "civgenesis-shard-");
    }

    @Bean
    @ConditionalOnMissingBean
    public PayloadCodec civgenesisPayloadCodec() {
        return new ProtobufPayloadCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public RouteTable civgenesisRouteTable(ApplicationContext ctx) {
        String[] names = ctx.getBeanNamesForAnnotation(GameController.class);
        List<Object> controllers = Arrays.stream(names).map(ctx::getBean).toList();
        return new RouteScanner().scan(controllers);
    }

    @Bean
    @ConditionalOnMissingBean(Dispatcher.class)
    public Dispatcher civgenesisDispatcher(
            RouteTable routeTable,
            PayloadCodec codec,
            ShardExecutor shardExecutor,
            CivMetrics metrics,
            CivTracer tracer,
            CivgenesisDispatcherProperties props
    ) {
        DispatcherConfig config = new DispatcherConfig(
                props.getMaxInFlightPerConnection(),
                props.getRawPayloadMode(),
                props.isCloseOnNeedLogin(),
                props.isDedupEnabled(),
                props.getDedupMaxEntries(),
                props.getDedupTtlMillis(),
                props.getMaxBufferedPushCount(),
                props.getMaxBufferedPushAgeMillis()
        );
        return new DispatcherRuntime(routeTable, codec, shardExecutor, config, metrics, tracer);
    }
}
