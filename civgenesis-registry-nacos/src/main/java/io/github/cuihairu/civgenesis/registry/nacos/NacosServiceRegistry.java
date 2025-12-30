package io.github.cuihairu.civgenesis.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.github.cuihairu.civgenesis.registry.RegistryMetadata;
import io.github.cuihairu.civgenesis.registry.ServiceDiscovery;
import io.github.cuihairu.civgenesis.registry.ServiceInstance;
import io.github.cuihairu.civgenesis.registry.ServiceRegistration;
import io.github.cuihairu.civgenesis.registry.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class NacosServiceRegistry implements ServiceRegistry, ServiceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

    private final NacosRegistryConfig config;
    private final NamingService namingService;

    public NacosServiceRegistry(NacosRegistryConfig config) throws NacosException {
        this.config = Objects.requireNonNullElse(config, NacosRegistryConfig.defaults());
        Properties props = new Properties();
        props.setProperty("serverAddr", this.config.serverAddr());
        if (this.config.namespace() != null && !this.config.namespace().isBlank()) {
            props.setProperty("namespace", this.config.namespace());
        }
        if (this.config.username() != null && !this.config.username().isBlank()) {
            props.setProperty("username", this.config.username());
        }
        if (this.config.password() != null && !this.config.password().isBlank()) {
            props.setProperty("password", this.config.password());
        }
        this.namingService = NacosFactory.createNamingService(props);
    }

    public NamingService namingService() {
        return namingService;
    }

    @Override
    public void register(ServiceRegistration registration) throws Exception {
        Objects.requireNonNull(registration, "registration");
        Instance instance = new Instance();
        instance.setIp(registration.ip());
        instance.setPort(registration.port());
        instance.setInstanceId(registration.instanceId());
        instance.setEphemeral(true);
        instance.setHealthy(true);

        var md = new java.util.HashMap<>(registration.metadata());
        md.putIfAbsent(RegistryMetadata.CG_INSTANCE_ID, Long.toUnsignedString(registration.instanceIdLong()));
        md.putIfAbsent(RegistryMetadata.CG_TRANSPORT_CAPS, Long.toUnsignedString(registration.transportCaps()));
        if (!md.containsKey(RegistryMetadata.CG_ENDPOINTS) && registration.endpoints() != null) {
            md.putAll(RegistryMetadata.encode(registration.instanceIdLong(), registration.transportCaps(), registration.endpoints()));
        }
        instance.setMetadata(md);

        namingService.registerInstance(registration.serviceName(), registration.group(), instance);
        log.info("nacos register serviceName={} group={} instanceId={}", registration.serviceName(), registration.group(), registration.instanceId());
    }

    @Override
    public void deregister(ServiceRegistration registration) throws Exception {
        Objects.requireNonNull(registration, "registration");
        namingService.deregisterInstance(registration.serviceName(), registration.group(), registration.ip(), registration.port(), registration.instanceId());
        log.info("nacos deregister serviceName={} group={} instanceId={}", registration.serviceName(), registration.group(), registration.instanceId());
    }

    @Override
    public List<ServiceInstance> list(String serviceName) throws Exception {
        List<Instance> instances = namingService.getAllInstances(serviceName, config.group(), true);
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        ArrayList<ServiceInstance> result = new ArrayList<>(instances.size());
        for (Instance instance : instances) {
            var md = instance.getMetadata() == null ? java.util.Map.<String, String>of() : instance.getMetadata();
            long instanceIdLong = 0;
            long caps = 0;
            try {
                instanceIdLong = RegistryMetadata.decodeInstanceId(md);
                caps = RegistryMetadata.decodeTransportCaps(md);
            } catch (Exception ignored) {
            }
            result.add(new ServiceInstance(
                    serviceName,
                    instance.getInstanceId(),
                    instanceIdLong,
                    instance.getIp(),
                    instance.getPort(),
                    caps,
                    RegistryMetadata.decodeEndpoints(md),
                    md
            ));
        }
        return result;
    }

    @Override
    public void close() {
        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.warn("nacos shutdown error", e);
        }
    }
}

