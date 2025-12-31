package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.ipc.IpcDialer;
import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.nio.file.Path;
import java.util.Objects;

public final class UdsIpcDialer implements IpcDialer {
    private final UdsIpcOptions options;

    public UdsIpcDialer(UdsIpcOptions options) {
        this.options = Objects.requireNonNullElse(options, UdsIpcOptions.defaults());
    }

    @Override
    public IpcLink connect(Endpoint endpoint, IpcMessageHandler handler) throws Exception {
        Path path = UdsEndpoint.toPath(endpoint);
        return UdsIpcClient.connect(path, options, handler);
    }
}

