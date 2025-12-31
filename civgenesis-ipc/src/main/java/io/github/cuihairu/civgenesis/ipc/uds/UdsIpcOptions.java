package io.github.cuihairu.civgenesis.ipc.uds;

public record UdsIpcOptions(
        int maxFrameBytes,
        int maxQueueBytes
) {
    public static UdsIpcOptions defaults() {
        return new UdsIpcOptions(1024 * 1024, 4 * 1024 * 1024);
    }
}

