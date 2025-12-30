package io.github.cuihairu.civgenesis.system.snapshot;

public interface SnapshotProvider {
    SnapshotResult snapshot(long playerId, long clientStateRevision) throws Exception;
}

