package io.github.cuihairu.civgenesis.rpc.grpc;

import java.net.URI;
import java.util.Objects;

public record GrpcEndpoint(
        String host,
        int port
) {
    public static GrpcEndpoint fromUri(String uri) {
        Objects.requireNonNull(uri, "uri");
        URI u = URI.create(uri);
        if (!"grpc".equalsIgnoreCase(u.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme for grpc endpoint: " + u.getScheme());
        }
        String host = u.getHost();
        int port = u.getPort();
        if (host == null || host.isBlank() || port <= 0) {
            throw new IllegalArgumentException("Invalid grpc endpoint uri: " + uri);
        }
        return new GrpcEndpoint(host, port);
    }
}

