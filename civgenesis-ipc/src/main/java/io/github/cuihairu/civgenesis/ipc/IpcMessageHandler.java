package io.github.cuihairu.civgenesis.ipc;

@FunctionalInterface
public interface IpcMessageHandler {
    void onMessage(IpcLink link, byte[] message) throws Exception;
}

