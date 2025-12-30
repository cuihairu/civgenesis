package io.github.cuihairu.civgenesis.core.observability;

import io.github.cuihairu.civgenesis.core.protocol.FrameType;

public interface CivMetrics {
    default void onConnectionOpen(String transport) {}

    default void onConnectionClose(String transport) {}

    default void onFrameIn(String transport, FrameType type, int msgId, int bytes) {}

    default void onFrameOut(String transport, FrameType type, int msgId, int bytes) {}

    default void onInFlightInc() {}

    default void onInFlightDec() {}

    default void onRequestComplete(int msgId, boolean success, int errorCode, long durationNanos) {}

    default void onJobRun(String jobName, boolean success, long durationNanos) {}

    static CivMetrics noop() {
        return NoopCivMetrics.INSTANCE;
    }
}
