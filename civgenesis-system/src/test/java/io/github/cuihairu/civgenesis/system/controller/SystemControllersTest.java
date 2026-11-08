package io.github.cuihairu.civgenesis.system.controller;

import io.github.cuihairu.civgenesis.codec.protobuf.ProtobufPayloadCodec;
import io.github.cuihairu.civgenesis.core.error.CivError;
import io.github.cuihairu.civgenesis.core.error.CivErrorCodes;
import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.github.cuihairu.civgenesis.core.protocol.Frame;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.github.cuihairu.civgenesis.core.protocol.ProtocolFlags;
import io.github.cuihairu.civgenesis.dispatcher.runtime.DispatcherConfig;
import io.github.cuihairu.civgenesis.dispatcher.runtime.DispatcherRuntime;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RawPayloadMode;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteScanner;
import io.github.cuihairu.civgenesis.dispatcher.route.RouteTable;
import io.github.cuihairu.civgenesis.protocol.system.ClientHelloReq;
import io.github.cuihairu.civgenesis.protocol.system.ClientHelloResp;
import io.github.cuihairu.civgenesis.protocol.system.ResumeReq;
import io.github.cuihairu.civgenesis.protocol.system.ResumeResp;
import io.github.cuihairu.civgenesis.protocol.system.SyncReq;
import io.github.cuihairu.civgenesis.protocol.system.SyncSnapshotPush;
import io.github.cuihairu.civgenesis.protocol.system.SystemMsgIds;
import io.github.cuihairu.civgenesis.system.auth.AuthResult;
import io.github.cuihairu.civgenesis.system.auth.DenyAllTokenAuthenticator;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotProvider;
import io.github.cuihairu.civgenesis.system.snapshot.SnapshotResult;
import io.github.cuihairu.civgenesis.system.snapshot.UnsupportedSnapshotProvider;
import io.github.cuihairu.civgenesis.system.SystemServerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemControllersTest {
    private static DispatcherConfig config() {
        return new DispatcherConfig(8, 0, RawPayloadMode.RETAIN, false, 100, 0, false, 0, 0, 4, 60_000);
    }

    @Test
    void clientHelloNegotiatesCompressionLimitsAndProtocol() throws Exception {
        SystemServerConfig serverConfig = new SystemServerConfig(7, 1000, 3, 4, 5, "epoch", true);
        SystemClientHelloController controller = new SystemClientHelloController(serverConfig);
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(routes(controller), new ProtobufPayloadCodec(), shards, config());
            SystemTestConnection connection = new SystemTestConnection(1);
            runtime.onConnect(connection);
            Frame request = new Frame(FrameType.REQ, SystemMsgIds.CLIENT_HELLO, 1, 0, 0, 0, 0,
                    codec().encode(connection.alloc(), ClientHelloReq.newBuilder()
                            .setMaxFrameBytes(100)
                            .addSupportedCompressions(io.github.cuihairu.civgenesis.protocol.system.CompressionAlgorithm.COMPRESSION_GZIP)
                            .addSupportedCiphers(io.github.cuihairu.civgenesis.protocol.system.CipherAlgorithm.CIPHER_NONE)
                            .build()).retain());
            runtime.handle(connection, request);
            await().atMost(Duration.ofSeconds(10)).until(() -> !connection.frames.isEmpty());
            ClientHelloResp resp = ClientHelloResp.parseFrom(connection.frames.get(0).payload());
            assertEquals(100, resp.getMaxFrameBytes());
            assertEquals(io.github.cuihairu.civgenesis.protocol.system.CompressionAlgorithm.COMPRESSION_GZIP, resp.getSelectedCompression());
            assertEquals(io.github.cuihairu.civgenesis.protocol.system.CipherAlgorithm.CIPHER_NONE, resp.getSelectedCipher());
            assertEquals(7, resp.getProtocolVersion());
            assertEquals("epoch", resp.getServerEpoch());
            assertThrows(NullPointerException.class, () -> new SystemClientHelloController(null));
        }
    }

    @Test
    void resumeCoversUnauthorizedExpiredEpochSuccessAndReplay() throws Exception {
        SystemResumeController controller = new SystemResumeController(
                new SystemServerConfig(1, 1000, 8, 8, 60_000, "epoch", false),
                token -> {
                    if (token.equals("fail")) throw new IllegalStateException("bad token");
                    if (token.equals("deny")) return AuthResult.of(0);
                    return AuthResult.of(9);
                });
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(routes(controller), new ProtobufPayloadCodec(), shards, config());
            SystemTestConnection connection = new SystemTestConnection(1);
            runtime.onConnect(connection);

            resume(runtime, connection, 1, "");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 1);
            assertEquals(CivErrorCodes.UNAUTHORIZED, error(connection.frames.get(0)));

            resume(runtime, connection, 2, "good", "old");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 2);
            assertEquals(ResumeResp.Result.RESUME_NEED_FULL_SYNC, ResumeResp.parseFrom(connection.frames.get(1).payload()).getResult());

            resume(runtime, connection, 3, "fail");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 3);
            assertEquals(CivErrorCodes.UNAUTHORIZED, error(connection.frames.get(2)));

            resume(runtime, connection, 4, "deny");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 4);
            assertEquals(CivErrorCodes.UNAUTHORIZED, error(connection.frames.get(3)));

            resume(runtime, connection, 5, "good");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 5);
            ResumeResp ok = ResumeResp.parseFrom(connection.frames.get(4).payload());
            assertEquals(ResumeResp.Result.RESUME_OK, ok.getResult());
            assertEquals(0, ok.getMaxBufferedPushId());

            resume(runtime, connection, 6, "good");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 6);
            resume(runtime, connection, 7, "good", "epoch", 99);
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 7);
            assertEquals(ResumeResp.Result.RESUME_NEED_FULL_SYNC, ResumeResp.parseFrom(connection.frames.get(connection.frames.size() - 1).payload()).getResult());
            assertThrows(NullPointerException.class, () -> new SystemResumeController(null, new DenyAllTokenAuthenticator()));
        }
    }

    @Test
    void syncSendsSnapshotPushAndFallsBackToEmptyResponse() throws Exception {
        SnapshotProvider success = (playerId, revision) -> new SnapshotResult(77, "snapshot".getBytes());
        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    routes(resumeController(), new SystemSyncController(success)), new ProtobufPayloadCodec(), shards, config());
            SystemTestConnection connection = new SystemTestConnection(1);
            runtime.onConnect(connection);
            resume(runtime, connection, 1, "good");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 1);
            Frame request = new Frame(FrameType.REQ, SystemMsgIds.SYNC, 2, 0, 0, 0, 0,
                    codec().encode(connection.alloc(), SyncReq.newBuilder().setClientStateRevision(5).build()).retain());
            runtime.handle(connection, request);
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 3);
            SyncSnapshotPush push = SyncSnapshotPush.parseFrom(connection.frames.get(2).payload());
            assertEquals(77, push.getServerStateRevision());
            assertEquals("snapshot", push.getSnapshot().toStringUtf8());
            assertEquals(ProtocolFlags.ACK_REQUIRED, connection.frames.get(2).flags());
        }

        try (ShardExecutor shards = new ShardExecutor(1, "test")) {
            DispatcherRuntime runtime = new DispatcherRuntime(
                    routes(resumeController(), new SystemSyncController(new UnsupportedSnapshotProvider())), new ProtobufPayloadCodec(), shards, config());
            SystemTestConnection connection = new SystemTestConnection(1);
            runtime.onConnect(connection);
            resume(runtime, connection, 1, "good");
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 1);
            Frame request = new Frame(FrameType.REQ, SystemMsgIds.SYNC, 2, 0, 0, 0, 0,
                    codec().encode(connection.alloc(), SyncReq.newBuilder().setClientStateRevision(5).build()).retain());
            runtime.handle(connection, request);
            await().atMost(Duration.ofSeconds(10)).until(() -> connection.frames.size() == 2);
            assertEquals(io.github.cuihairu.civgenesis.protocol.system.SyncResp.getDefaultInstance(),
                    io.github.cuihairu.civgenesis.protocol.system.SyncResp.parseFrom(connection.frames.get(1).payload()));
        }
        assertThrows(NullPointerException.class, () -> new SystemSyncController(null));
        assertTrue(new DenyAllTokenAuthenticator().authenticate("x").playerId() <= 0);
        assertThrows(UnsupportedOperationException.class, () -> new UnsupportedSnapshotProvider().snapshot(1, 1));
        assertEquals(0, AuthResult.of(4).kickExistingSession() ? 0 : 1);
        assertEquals("snapshot", new String(new SnapshotResult(1, "snapshot".getBytes()).snapshotBytes()));
    }

    private static SystemResumeController resumeController() {
        return new SystemResumeController(
                new SystemServerConfig(1, 1000, 8, 8, 60_000, "epoch", false),
                token -> AuthResult.of(9));
    }

    static void resume(DispatcherRuntime runtime, SystemTestConnection connection, long seq, String token) throws Exception {
        resume(runtime, connection, seq, token, "epoch", 0);
    }

    static void resume(DispatcherRuntime runtime, SystemTestConnection connection, long seq, String token, String epoch) throws Exception {
        resume(runtime, connection, seq, token, epoch, 0);
    }

    static void resume(DispatcherRuntime runtime, SystemTestConnection connection, long seq, String token, String epoch, long lastApplied) throws Exception {
        Frame request = new Frame(FrameType.REQ, SystemMsgIds.RESUME, seq, 0, 0, 0, 0,
                codec().encode(connection.alloc(), ResumeReq.newBuilder()
                        .setToken(token)
                        .setServerEpoch(epoch)
                        .setLastAppliedPushId(lastApplied)
                        .build()).retain());
        runtime.handle(connection, request);
    }

    private static RouteTable routes(Object... controllers) {
        return new RouteScanner().scan(List.of(controllers));
    }

    static ProtobufPayloadCodec codec() {
        return new ProtobufPayloadCodec();
    }

    private static int error(SystemTestConnection.CapturedFrame frame) throws Exception {
        assertTrue((frame.flags() & ProtocolFlags.ERROR) != 0);
        return io.github.cuihairu.civgenesis.protocol.system.Error.parseFrom(frame.payload()).getCode();
    }
}
