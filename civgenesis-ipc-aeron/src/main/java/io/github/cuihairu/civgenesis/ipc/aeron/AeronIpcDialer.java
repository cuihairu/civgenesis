package io.github.cuihairu.civgenesis.ipc.aeron;

import io.aeron.Aeron;
import io.github.cuihairu.civgenesis.ipc.IpcDialer;
import io.github.cuihairu.civgenesis.ipc.IpcLink;
import io.github.cuihairu.civgenesis.ipc.IpcMessageHandler;
import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public final class AeronIpcDialer implements IpcDialer, AutoCloseable {
    private final Aeron aeron;

    public AeronIpcDialer(Path aeronDir) {
        Objects.requireNonNull(aeronDir, "aeronDir");
        Aeron.Context ctx = new Aeron.Context().aeronDirectoryName(aeronDir.toString());
        this.aeron = Aeron.connect(ctx);
    }

    @Override
    public IpcLink connect(Endpoint endpoint, IpcMessageHandler handler) {
        Objects.requireNonNull(endpoint, "endpoint");
        URI uri = URI.create(endpoint.uri());
        if (!"aeron".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("not an aeron endpoint: " + endpoint.uri());
        }
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null || !ssp.startsWith("ipc")) {
            throw new IllegalArgumentException("not an aeron ipc endpoint: " + endpoint.uri());
        }
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("missing query for aeron endpoint: " + endpoint.uri());
        }
        int streamId = parseIntQuery(query, "streamId", -1);
        if (streamId <= 0) {
            throw new IllegalArgumentException("missing/invalid streamId: " + endpoint.uri());
        }
        return new AeronIpcChannel(aeron, streamId, handler);
    }

    @Override
    public void close() {
        aeron.close();
    }

    private static int parseIntQuery(String query, String key, int defaultValue) {
        String[] parts = query.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = part.substring(0, idx);
            if (!k.equals(key)) {
                continue;
            }
            String v = part.substring(idx + 1);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignore) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

