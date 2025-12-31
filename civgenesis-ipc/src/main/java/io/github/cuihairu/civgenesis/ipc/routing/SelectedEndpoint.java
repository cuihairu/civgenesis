package io.github.cuihairu.civgenesis.ipc.routing;

import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.util.Objects;

public record SelectedEndpoint(EndpointKind kind, Endpoint endpoint) {
    public SelectedEndpoint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(endpoint, "endpoint");
    }
}

