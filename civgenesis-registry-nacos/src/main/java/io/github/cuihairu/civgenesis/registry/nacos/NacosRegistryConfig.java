package io.github.cuihairu.civgenesis.registry.nacos;

import java.util.Objects;

public record NacosRegistryConfig(
        String serverAddr,
        String namespace,
        String username,
        String password,
        String group
) {
    public NacosRegistryConfig {
        Objects.requireNonNull(serverAddr, "serverAddr");
        Objects.requireNonNull(group, "group");
    }

    public static NacosRegistryConfig defaults() {
        return new NacosRegistryConfig("127.0.0.1:8848", "", "", "", "DEFAULT_GROUP");
    }
}

