package io.github.cuihairu.civgenesis.rpc.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Objects;

public final class GrpcChannels {
    private GrpcChannels() {}

    public static ManagedChannel forEndpoint(GrpcEndpoint endpoint, GrpcClientConfig config) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(config, "config");
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(endpoint.host(), endpoint.port());
        if (config.plaintext()) {
            builder.usePlaintext();
        }
        return builder.build();
    }

    public static ManagedChannel forUri(String uri, GrpcClientConfig config) {
        return forEndpoint(GrpcEndpoint.fromUri(uri), config);
    }
}

