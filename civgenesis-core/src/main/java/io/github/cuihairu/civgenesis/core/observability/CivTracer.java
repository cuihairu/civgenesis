package io.github.cuihairu.civgenesis.core.observability;

public interface CivTracer {
    default CivSpan startRequestSpan(long connectionId, long playerId, long sessionEpoch, int msgId, long seq) {
        return CivSpan.noop();
    }

    default CivSpan startJobSpan(String jobName, long runId) {
        return CivSpan.noop();
    }

    static CivTracer noop() {
        return NoopCivTracer.INSTANCE;
    }
}
