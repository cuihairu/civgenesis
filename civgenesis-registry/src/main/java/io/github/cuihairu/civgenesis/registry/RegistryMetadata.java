package io.github.cuihairu.civgenesis.registry;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class RegistryMetadata {
    private RegistryMetadata() {}

    public static final String CG_INSTANCE_ID = "cg.instanceId";
    public static final String CG_TRANSPORT_CAPS = "cg.transportCaps";
    public static final String CG_ENDPOINTS = "cg.endpoints";

    public static Map<String, String> encode(long instanceId, long transportCaps, List<Endpoint> endpoints) {
        HashMap<String, String> md = new HashMap<>();
        md.put(CG_INSTANCE_ID, Long.toUnsignedString(instanceId));
        md.put(CG_TRANSPORT_CAPS, Long.toUnsignedString(transportCaps));
        md.put(CG_ENDPOINTS, encodeEndpoints(endpoints));
        return md;
    }

    public static long decodeInstanceId(Map<String, String> metadata) {
        String v = metadata.getOrDefault(CG_INSTANCE_ID, "0");
        return Long.parseUnsignedLong(v);
    }

    public static long decodeTransportCaps(Map<String, String> metadata) {
        String v = metadata.getOrDefault(CG_TRANSPORT_CAPS, "0");
        return Long.parseUnsignedLong(v);
    }

    public static List<Endpoint> decodeEndpoints(Map<String, String> metadata) {
        String raw = metadata.getOrDefault(CG_ENDPOINTS, "");
        if (raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        ArrayList<Endpoint> endpoints = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String uri = URLDecoder.decode(part, StandardCharsets.UTF_8);
            endpoints.add(new Endpoint(uri));
        }
        return endpoints;
    }

    private static String encodeEndpoints(List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Endpoint endpoint : endpoints) {
            joiner.add(URLEncoder.encode(endpoint.uri(), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }
}

