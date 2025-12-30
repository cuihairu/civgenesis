package io.github.cuihairu.civgenesis.system.controller;

import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.protocol.system.SyncReq;
import io.github.cuihairu.civgenesis.protocol.system.SyncResp;
import io.github.cuihairu.civgenesis.protocol.system.SyncSnapshotPush;
import io.github.cuihairu.civgenesis.protocol.system.SystemMsgIds;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotResult;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@GameController
public final class SystemSyncController {
    private static final Logger log = LoggerFactory.getLogger(SystemSyncController.class);

    private final SnapshotProvider snapshotProvider;

    public SystemSyncController(SnapshotProvider snapshotProvider) {
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider, "snapshotProvider");
    }

    @GameRoute(id = SystemMsgIds.SYNC)
    public void sync(RequestContext ctx, SyncReq req) {
        try {
            SnapshotResult snapshot = snapshotProvider.snapshot(ctx.playerId(), req.getClientStateRevision());
            ctx.reply(SyncResp.getDefaultInstance());
            SyncSnapshotPush push = SyncSnapshotPush.newBuilder()
                    .setServerStateRevision(snapshot.serverStateRevision())
                    .setSnapshot(ByteString.copyFrom(snapshot.snapshotBytes()))
                    .build();
            ctx.push(SystemMsgIds.SYNC_SNAPSHOT_PUSH, push, ProtocolFlags.ACK_REQUIRED);
        } catch (Exception e) {
            log.warn("sync snapshot failed", e);
            ctx.reply(SyncResp.getDefaultInstance());
        }
    }
}
