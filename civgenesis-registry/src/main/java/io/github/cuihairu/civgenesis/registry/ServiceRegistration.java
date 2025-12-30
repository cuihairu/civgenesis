package io.github.cuihairu.civgenesis.registry;

import java.util.List;
import java.util.Map;

public record ServiceRegistration(
        String serviceName,
        String group,
        String ip,
        int port,
        String instanceId,
        long instanceIdLong,
        long transportCaps,
        List<Endpoint> endpoints,
        Map<String, String> metadata
) {}

