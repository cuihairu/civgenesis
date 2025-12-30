package io.github.cuihairu.civgenesis.dispatcher.runtime;

public record DispatcherConfig(
        int maxInFlightPerConnection,
        RawPayloadMode rawPayloadMode,
        boolean closeOnNeedLogin
) {
    public static DispatcherConfig defaults() {
        return new DispatcherConfig(64, RawPayloadMode.RETAIN, false);
    }
}

