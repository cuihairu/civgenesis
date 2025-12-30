package io.github.cuihairu.civgenesis.system.snapshot;

public record SnapshotResult(
        long serverStateRevision,
        byte[] snapshotBytes
) {
}

