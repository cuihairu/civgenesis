package io.github.cuihairu.civgenesis.spring.registry;

import io.github.cuihairu.civgenesis.registry.nacos.NacosRegistryConfig;
import io.github.cuihairu.civgenesis.registry.nacos.NacosServiceRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CivgenesisNacosProperties.class)
@ConditionalOnClass(NacosServiceRegistry.class)
@ConditionalOnProperty(prefix = "civgenesis.registry.nacos", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CivgenesisRegistryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NacosServiceRegistry civgenesisNacosServiceRegistry(CivgenesisNacosProperties props) throws Exception {
        NacosRegistryConfig config = new NacosRegistryConfig(
                props.getServerAddr(),
                props.getNamespace(),
                props.getUsername(),
                props.getPassword(),
                props.getGroup()
        );
        return new NacosServiceRegistry(config);
    }
}

