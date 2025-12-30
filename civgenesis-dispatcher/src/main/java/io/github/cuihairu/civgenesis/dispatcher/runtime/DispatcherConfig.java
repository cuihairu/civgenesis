package io.github.cuihairu.civgenesis.dispatcher.runtime;

public record DispatcherConfig(
        int maxInFlightPerConnection,
        RawPayloadMode rawPayloadMode,
        boolean closeOnNeedLogin,
        boolean dedupEnabled,
        int dedupMaxEntries,
        long dedupTtlMillis,
        int maxBufferedPushCount,
        long maxBufferedPushAgeMillis
) {
    public static DispatcherConfig defaults() {
        return new DispatcherConfig(
                64,
                RawPayloadMode.RETAIN,
                false,
                true,
                1024,
                30_000,
                2000,
                60_000
        );
    }
}
