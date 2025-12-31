package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.Aeron;
import io.github.cuihairu.civgenesis.ipc.IpcDialer;
import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public final class AeronIpcDialer implements IpcDialer, AutoCloseable {
    private final String defaultDir;
    private final ConcurrentHashMap<String, Aeron> clients = new ConcurrentHashMap<>();

    public AeronIpcDialer(Path aeronDir) {
        Objects.requireNonNull(aeronDir, "aeronDir");
        this.defaultDir = aeronDir.toString();
    }

    public AeronIpcDialer() {
        this.defaultDir = null;
    }

    @Override
    public IpcLink connect(Endpoint endpoint, IpcMessageHandler handler) {
        AeronIpcEndpoint parsed = AeronIpcEndpoint.parse(endpoint, defaultDir);
        Aeron aeron = clients.computeIfAbsent(parsed.dir(), dir -> Aeron.connect(new Aeron.Context().aeronDirectoryName(dir)));
        if (parsed.inStreamId() == parsed.outStreamId()) {
            return new AeronIpcChannel(aeron, parsed.inStreamId(), handler);
        }
        return new AeronDuplexIpcChannel(aeron, parsed.inStreamId(), parsed.outStreamId(), handler);
    }

    @Override
    public void close() {
        for (Map.Entry<String, Aeron> e : clients.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ignore) {
            }
        }
        clients.clear();
    }
}
