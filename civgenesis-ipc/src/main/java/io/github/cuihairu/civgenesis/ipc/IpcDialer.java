package io.github.cuihairu.civgenesis.ipc;

import io.github.cuihairu.civgenesis.registry.Endpoint;

public interface IpcDialer {
    IpcLink connect(Endpoint endpoint, IpcMessageHandler handler) throws Exception;
}

