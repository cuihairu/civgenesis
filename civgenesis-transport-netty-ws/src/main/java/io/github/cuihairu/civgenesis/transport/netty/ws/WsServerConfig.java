package io.github.cuihairu.civgenesis.transport.netty.ws;

public record WsServerConfig(
        int bossThreads,
        int workerThreads,
        int port,
        String path,
        int soBacklog,
        int recvBufBytes,
        int sendBufBytes,
        boolean pooledAllocator,
        int maxFrameBytes,
        int idleTimeoutSeconds,
        boolean pingBeforeClose,
        int pingTimeoutMillis
) {
    public static WsServerConfig defaults() {
        return new WsServerConfig(
                1,
                0,
                8888,
                "/",
                1024,
                0,
                0,
                true,
                1024 * 1024,
                30,
                true,
                3000
        );
    }
}
