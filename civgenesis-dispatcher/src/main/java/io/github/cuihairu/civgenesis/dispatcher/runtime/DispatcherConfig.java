package io.github.cuihairu.civgenesis.dispatcher.runtime;

public record DispatcherConfig(
        int maxInFlightPerConnection,
        int maxInFlightPerShard,
        RawPayloadMode rawPayloadMode,
        boolean closeOnNeedLogin,
        long requestTimeoutMillis,
        long slowRequestMillis,
        boolean dedupEnabled,
        int dedupMaxEntries,
        long dedupTtlMillis,
        int maxBufferedPushCount,
        long maxBufferedPushAgeMillis
) {
    public static DispatcherConfig defaults() {
        return new DispatcherConfig(
                64,
                2048,
                RawPayloadMode.RETAIN,
                false,
                5_000,
                200,
                true,
                1024,
                30_000,
                2000,
                60_000
        );
    }
}
