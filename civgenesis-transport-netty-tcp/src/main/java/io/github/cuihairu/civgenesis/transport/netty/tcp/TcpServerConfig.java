package io.github.cuihairu.civgenesis.transport.netty.tcp;

public record TcpServerConfig(
        int bossThreads,
        int workerThreads,
        String host,
        int port,
        int soBacklog,
        int recvBufBytes,
        int sendBufBytes,
        boolean pooledAllocator,
        int maxFrameBytes,
        int idleTimeoutSeconds
) {
    public static TcpServerConfig defaults() {
        return new TcpServerConfig(
                1,
                0,
                "0.0.0.0",
                9999,
                1024,
                0,
                0,
                true,
                1024 * 1024,
                30
        );
    }
}

