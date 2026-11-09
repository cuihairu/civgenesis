package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.protocol.Compression;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatcherRuntimeUnitsTest {

    @Test
    void gzipCompressDecompressRoundTrips() throws Exception {
        UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        String text = "the quick brown fox jumps over the lazy dog";
        ByteBuf src = Unpooled.copiedBuffer(text, StandardCharsets.UTF_8);
        ByteBuf compressed = GzipPayloads.compress(alloc, src);
        ByteBuf decompressed = GzipPayloads.decompress(alloc, compressed);
        assertEquals(text, decompressed.toString(StandardCharsets.UTF_8));
        src.release();
        compressed.release();
        decompressed.release();
    }

    @Test
    void gzipHandlesNullOrEmptyInput() throws Exception {
        UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        ByteBuf a = GzipPayloads.compress(alloc, null);
        assertEquals(0, a.readableBytes());
        a.release();
        ByteBuf b = GzipPayloads.decompress(alloc, null);
        assertEquals(0, b.readableBytes());
        b.release();
        ByteBuf empty = Unpooled.buffer(0);
        ByteBuf c = GzipPayloads.compress(alloc, empty);
        assertEquals(0, c.readableBytes());
        c.release();
        ByteBuf d = GzipPayloads.decompress(alloc, empty);
        assertEquals(0, d.readableBytes());
        d.release();
        empty.release();
    }

    @Test
    void decompressRejectsInvalidGzipData() {
        UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;
        ByteBuf notGzip = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        try {
            assertThrows(java.io.IOException.class, () -> GzipPayloads.decompress(alloc, notGzip));
        } finally {
            notGzip.release();
        }
    }

    @Test
    void pushEntryRecordExposesValueSemantics() {
        // the same array reference is shared so the record's equals() runs all component checks
        byte[] payload = new byte[]{1};
        PlayerSession.PushEntry a = new PlayerSession.PushEntry(1, 7, 0, 0, payload);
        PlayerSession.PushEntry b = new PlayerSession.PushEntry(1, 7, 0, 0, payload);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.toString().contains("pushId"));
        assertEquals(1, a.pushId());
        assertEquals(7, a.msgId());
        assertEquals(0, a.flags());
        assertEquals(0, a.tsMillis());
        assertEquals(1, a.payloadBytes().length);
    }

    @Test
    void dedupCacheStoresEvictsAndExpires() {
        ResponseDedupCache cache = new ResponseDedupCache(2, 100);
        assertNull(cache.get(1, 0));
        cache.put(1, 7, 0, new byte[]{1}, 0);
        assertNotNull(cache.get(1, 50));
        cache.put(2, 8, 0, new byte[]{2}, 0);
        cache.put(3, 9, 0, new byte[]{3}, 0);
        assertNull(cache.get(1, 0));
        assertNotNull(cache.get(3, 0));
        assertNull(cache.get(2, 200));
    }

    @Test
    void dedupCacheDisabledWhenLimitsAreZero() {
        ResponseDedupCache cache = new ResponseDedupCache(0, 0);
        assertNull(cache.get(1, 0));
        cache.put(1, 7, 0, new byte[]{1}, 0);
        assertNull(cache.get(1, 0));
    }

    @Test
    void playerSessionReplayAndAck() {
        PlayerSession session = new PlayerSession(5, 3, 100);
        assertEquals(5, session.playerId());
        assertEquals(1, session.nextPushId());
        assertEquals(2, session.nextPushId());

        session.recordPush(entry(1, 7, 0, new byte[]{1}));
        session.recordPush(entry(2, 8, 10, new byte[]{2}));
        PlayerSession.ReplayPlan full = session.planReplay(0, 50);
        assertTrue(full.ok());
        assertEquals(1, full.minBufferedPushId());
        assertEquals(2, full.maxBufferedPushId());
        assertEquals(2, full.toReplay().size());

        session.ack(1);
        session.ack(0); // stale ack is ignored
        assertEquals(1, session.planReplay(0, 50).toReplay().size());

        session.ack(2);
        PlayerSession.ReplayPlan empty = session.planReplay(5, 50);
        assertFalse(empty.ok());
        assertEquals(0, empty.minBufferedPushId());
        assertEquals(0, empty.maxBufferedPushId());
        assertEquals(0, empty.toReplay().size());
    }

    @Test
    void playerSessionReplayBoundaries() {
        PlayerSession session = new PlayerSession(1, 100, 1000);
        session.recordPush(entry(3, 1, 0, new byte[]{1}));
        session.recordPush(entry(4, 2, 0, new byte[]{2}));

        PlayerSession.ReplayPlan behind = session.planReplay(1, 50);
        assertFalse(behind.ok());
        assertEquals(3, behind.minBufferedPushId());
        assertEquals(4, behind.maxBufferedPushId());

        PlayerSession.ReplayPlan ahead = session.planReplay(9, 50);
        assertFalse(ahead.ok());

        PlayerSession.ReplayPlan partial = session.planReplay(3, 50);
        assertTrue(partial.ok());
        assertEquals(1, partial.toReplay().size());
        assertEquals(4, partial.toReplay().get(0).pushId());
    }

    @Test
    void playerSessionEvictsByCountAgeAndAck() {
        PlayerSession count = new PlayerSession(1, 2, 0);
        count.recordPush(entry(1, 1, 0, new byte[]{1}));
        count.recordPush(entry(2, 2, 0, new byte[]{2}));
        count.recordPush(entry(3, 3, 0, new byte[]{3}));
        assertEquals(2, count.planReplay(0, 0).toReplay().size());
        assertEquals(2, count.planReplay(0, 0).minBufferedPushId());

        PlayerSession age = new PlayerSession(1, 0, 100);
        age.recordPush(entry(1, 1, 0, new byte[]{1}));
        age.recordPush(entry(2, 2, 150, new byte[]{2}));
        PlayerSession.ReplayPlan expired = age.planReplay(0, 200);
        assertEquals(1, expired.toReplay().size());
        assertEquals(2, expired.minBufferedPushId());

        PlayerSession acked = new PlayerSession(1, 10, 0);
        acked.recordPush(entry(1, 1, 0, new byte[]{1}));
        acked.ack(1);
        acked.recordPush(entry(2, 2, 0, new byte[]{2}));
        assertEquals(2, acked.planReplay(0, 0).minBufferedPushId());
    }

    @Test
    void connectionStateTracksInFlightAndShardIndices() {
        ConnectionState state = new ConnectionState(42, config(2, 2));
        assertEquals(42, state.connectionId());
        assertEquals(0, state.inFlightCount());
        assertTrue(state.tryAddInFlight(1, 2, 0));
        assertTrue(state.tryAddInFlight(2, 2, 1));
        assertFalse(state.tryAddInFlight(1, 2, 0));
        assertFalse(state.tryAddInFlight(3, 2, 2));
        assertEquals(2, state.inFlightCount());
        assertEquals(0, state.removeInFlight(1));
        assertEquals(-1, state.removeInFlight(0));
        assertEquals(-1, state.removeInFlight(99));
        assertEquals(1, state.inFlightCount());

        assertTrue(state.tryAddInFlight(0, 2, 0)); // seq <= 0 is always admitted
        state.tryAddInFlight(10, 5, 1);
        state.tryAddInFlight(11, 5, 2);
        List<Integer> indices = state.clearInFlightShardIndices();
        assertEquals(3, indices.size());
        assertEquals(0, state.inFlightCount());
        assertEquals(0, state.clearInFlightShardIndices().size());
    }

    @Test
    void connectionStateRegistersAndRemovesDeferred() {
        ConnectionState state = new ConnectionState(42, config(2, 2));
        ConnectionState.DeferredEntry entry = deferred(5);
        assertTrue(state.registerDeferred(entry));
        assertFalse(state.registerDeferred(entry));
        assertFalse(state.registerDeferred(deferred(0)));
        assertNotNull(state.removeDeferred(5));
        assertNull(state.removeDeferred(5));
        assertNull(state.removeDeferred(0));

        state.registerDeferred(deferred(6));
        state.registerDeferred(deferred(7));
        assertEquals(2, state.removeAllDeferred().size());
        assertEquals(0, state.removeAllDeferred().size());
    }

    @Test
    void connectionStateAttachDetachAndMisc() {
        ConnectionState state = new ConnectionState(42, config(2, 2));
        state.attachPlayer(9);
        assertEquals(9, state.playerId());
        assertEquals(1, state.sessionEpoch());
        state.detachPlayer();
        assertEquals(0, state.playerId());
        assertEquals(2, state.sessionEpoch());

        state.compression(null);
        assertEquals(Compression.NONE, state.compression());
        state.compression(Compression.GZIP);
        assertEquals(Compression.GZIP, state.compression());

        assertNull(state.playerSession());
        state.playerSession(new PlayerSession(9, 5, 100));
        assertNotNull(state.playerSession());
    }

    private static DispatcherConfig config(int maxInFlight, int dedupMax) {
        return new DispatcherConfig(
                maxInFlight, 10, RawPayloadMode.RETAIN, false, 5_000, 200, true,
                dedupMax, 100, 5, 1_000);
    }

    private static ConnectionState.DeferredEntry deferred(long seq) {
        return new ConnectionState.DeferredEntry(seq, 7, 1, 0, 0, null);
    }

    private static PlayerSession.PushEntry entry(long pushId, int msgId, long tsMillis, byte[] payload) {
        return new PlayerSession.PushEntry(pushId, msgId, 0, tsMillis, payload);
    }
}
