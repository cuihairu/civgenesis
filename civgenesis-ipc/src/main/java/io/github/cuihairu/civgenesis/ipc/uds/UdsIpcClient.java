package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Objects;

public final class UdsIpcClient {
    private UdsIpcClient() {}

    public static IpcLink connect(Path path, UdsIpcOptions options, IpcMessageHandler handler) throws IOException {
        Objects.requireNonNull(path, "path");
        UdsIpcOptions opts = Objects.requireNonNullElse(options, UdsIpcOptions.defaults());
        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
        ch.connect(UnixDomainSocketAddress.of(path));
        return new UdsIpcConnection(ch, opts, handler);
    }
}

