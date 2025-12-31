package io.github.cuihairu.civgenesis.ipc.aeron;

import io.github.cuihairu.civgenesis.registry.Endpoint;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

record AeronIpcEndpoint(
        String dir,
        int inStreamId,
        int outStreamId
) {
    static AeronIpcEndpoint parse(Endpoint endpoint, String defaultDir) {
        Objects.requireNonNull(endpoint, "endpoint");
        URI uri = URI.create(endpoint.uri());
        String scheme = uri.getScheme();
        if (scheme == null || !"aeron".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("not an aeron endpoint: " + endpoint.uri());
        }
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) {
            throw new IllegalArgumentException("invalid aeron endpoint: " + endpoint.uri());
        }
        String[] sspParts = ssp.split("\\?", 2);
        String transport = sspParts[0];
        if (transport == null || !transport.toLowerCase(Locale.ROOT).startsWith("ipc")) {
            throw new IllegalArgumentException("not an aeron ipc endpoint: " + endpoint.uri());
        }
        String query = uri.isOpaque() ? (sspParts.length == 2 ? sspParts[1] : null) : uri.getQuery();
        Map<String, String> q = parseQuery(query);

        String dir = q.getOrDefault("dir", defaultDir);
        if (dir == null || dir.isBlank()) {
            throw new IllegalArgumentException("missing aeron dir (query.dir) and no default configured: " + endpoint.uri());
        }

        int streamId = parseInt(q.get("streamId"), -1);
        int in = parseInt(firstNonNull(q.get("inStreamId"), q.get("in")), -1);
        int out = parseInt(firstNonNull(q.get("outStreamId"), q.get("out")), -1);
        if (streamId > 0) {
            in = streamId;
            out = streamId;
        }
        if (in <= 0 || out <= 0) {
            throw new IllegalArgumentException("missing/invalid streamId (or in/out stream ids): " + endpoint.uri());
        }
        return new AeronIpcEndpoint(dir, in, out);
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        HashMap<String, String> out = new HashMap<>();
        String[] parts = query.split("&");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] kv = part.split("=", 2);
            String k = urlDecode(kv[0]).trim();
            String v = kv.length == 2 ? urlDecode(kv[1]).trim() : "";
            if (!k.isBlank()) {
                out.put(k, v);
            }
        }
        return out;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
