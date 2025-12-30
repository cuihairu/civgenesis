package io.github.cuihairu.civgenesis.registry;

import java.util.List;

public interface ServiceDiscovery {
    List<ServiceInstance> list(String serviceName) throws Exception;
}

