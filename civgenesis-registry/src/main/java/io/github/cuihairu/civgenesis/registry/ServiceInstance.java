package io.github.cuihairu.civgenesis.registry;

import java.util.List;
import java.util.Map;

public record ServiceInstance(
        String serviceName,
        String instanceId,
        long instanceIdLong,
        String ip,
        int port,
        long transportCaps,
        List<Endpoint> endpoints,
        Map<String, String> metadata
) {}

