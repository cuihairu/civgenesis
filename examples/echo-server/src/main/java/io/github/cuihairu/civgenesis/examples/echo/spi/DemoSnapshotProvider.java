package io.github.cuihairu.civgenesis.examples.echo.spi;

import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotResult;

import java.nio.charset.StandardCharsets;

public final class DemoSnapshotProvider implements SnapshotProvider {
    @Override
    public SnapshotResult snapshot(long playerId, long clientStateRevision) {
        byte[] bytes = ("snapshot(playerId=" + playerId + ",rev=" + clientStateRevision + ")").getBytes(StandardCharsets.UTF_8);
        return new SnapshotResult(System.currentTimeMillis(), bytes);
    }
}

