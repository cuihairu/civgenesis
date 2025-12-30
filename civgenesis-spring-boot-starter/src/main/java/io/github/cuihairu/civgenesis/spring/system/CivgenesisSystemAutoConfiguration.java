package io.github.cuihairu.civgenesis.spring.system;

import io.github.cuihairu.civgenesis.spring.CivgenesisDispatcherProperties;
import io.github.cuihairu.civgenesis.spring.CivgenesisWsProperties;
import io.github.cuihairu.civgenesis.system.SystemServerConfig;
import io.github.cuihairu.civgenesis.system.SystemServerEpoch;
import io.github.cuihairu.civgenesis.system.auth.DenyAllTokenAuthenticator;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;
import io.github.cuihairu.civgenesis.system.controller.SystemClientHelloController;
import io.github.cuihairu.civgenesis.system.controller.SystemResumeController;
import io.github.cuihairu.civgenesis.system.controller.SystemSyncController;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import io.github.cuihairu.civgenesis.system.snapshot.UnsupportedSnapshotProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisSystemProperties.class)
@ConditionalOnProperty(prefix = "civgenesis.system", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CivgenesisSystemAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SystemServerEpoch civgenesisSystemServerEpoch() {
        return new SystemServerEpoch();
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemServerConfig civgenesisSystemServerConfig(
            SystemServerEpoch epoch,
            CivgenesisWsProperties ws,
            CivgenesisDispatcherProperties dispatcher
    ) {
        return new SystemServerConfig(
                1,
                ws.getMaxFrameBytes(),
                dispatcher.getMaxInFlightPerConnection(),
                dispatcher.getMaxBufferedPushCount(),
                (int) dispatcher.getMaxBufferedPushAgeMillis(),
                epoch.value()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenAuthenticator civgenesisTokenAuthenticator() {
        return new DenyAllTokenAuthenticator();
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotProvider civgenesisSnapshotProvider() {
        return new UnsupportedSnapshotProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemClientHelloController civgenesisSystemClientHelloController(SystemServerConfig config) {
        return new SystemClientHelloController(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemResumeController civgenesisSystemResumeController(SystemServerConfig config, TokenAuthenticator authenticator) {
        return new SystemResumeController(config, authenticator);
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemSyncController civgenesisSystemSyncController(SnapshotProvider snapshotProvider) {
        return new SystemSyncController(snapshotProvider);
    }
}

