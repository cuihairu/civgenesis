package io.github.cuihairu.civgenesis.ipc;

public interface IpcLink extends AutoCloseable {
    boolean offer(byte[] message);

    @Override
    void close();
}

