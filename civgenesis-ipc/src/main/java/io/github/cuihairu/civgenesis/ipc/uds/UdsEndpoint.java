package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public final class UdsEndpoint {
    private UdsEndpoint() {}

    public static Path toPath(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        URI uri = URI.create(endpoint.uri());
        if (!"uds".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("not a uds endpoint: " + endpoint.uri());
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("missing uds path: " + endpoint.uri());
        }
        return Path.of(path);
    }
}

