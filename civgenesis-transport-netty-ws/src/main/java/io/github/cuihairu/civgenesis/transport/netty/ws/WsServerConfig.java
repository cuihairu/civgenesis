package io.github.cuihairu.civgenesis.transport.netty.ws;

public record WsServerConfig(
        int port,
        String path,
        int maxFrameBytes,
        int idleTimeoutSeconds,
        boolean pingBeforeClose,
        int pingTimeoutMillis
) {
    public static WsServerConfig defaults() {
        return new WsServerConfig(8888, "/", 1024 * 1024, 30, true, 3000);
    }
}

