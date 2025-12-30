package io.github.cuihairu.civgenesis.system;

public record SystemServerConfig(
        int protocolVersion,
        int maxFrameBytes,
        int maxInFlightReq,
        int maxBufferedPushCount,
        int maxBufferedPushAgeMillis,
        String serverEpoch
) {
    public static SystemServerConfig defaults(String serverEpoch) {
        return new SystemServerConfig(1, 1024 * 1024, 64, 2000, 60_000, serverEpoch);
    }
}

