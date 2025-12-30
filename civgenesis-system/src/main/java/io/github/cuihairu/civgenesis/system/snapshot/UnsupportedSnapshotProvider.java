package io.github.cuihairu.civgenesis.system.snapshot;

public final class UnsupportedSnapshotProvider implements SnapshotProvider {
    @Override
    public SnapshotResult snapshot(long playerId, long clientStateRevision) {
        throw new UnsupportedOperationException("snapshot provider not configured");
    }
}

