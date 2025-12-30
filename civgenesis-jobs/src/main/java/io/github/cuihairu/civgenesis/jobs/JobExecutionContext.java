package io.github.cuihairu.civgenesis.jobs;

public record JobExecutionContext(
        long runId,
        long startedAtEpochMillis
) {
}

