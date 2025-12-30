package io.github.cuihairu.civgenesis.registry;

import java.util.Objects;

public record Endpoint(String uri) {
    public Endpoint {
        Objects.requireNonNull(uri, "uri");
        if (uri.isBlank()) {
            throw new IllegalArgumentException("uri is blank");
        }
    }
}

