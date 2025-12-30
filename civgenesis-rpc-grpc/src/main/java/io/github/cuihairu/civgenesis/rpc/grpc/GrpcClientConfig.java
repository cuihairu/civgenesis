package io.github.cuihairu.civgenesis.rpc.grpc;

public record GrpcClientConfig(
        boolean plaintext
) {
    public static GrpcClientConfig insecure() {
        return new GrpcClientConfig(true);
    }

    public static GrpcClientConfig tls() {
        return new GrpcClientConfig(false);
    }
}
