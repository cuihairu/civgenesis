package io.github.cuihairu.civgenesis.dispatcher.runtime;

import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.protocol.Compression;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ConnectionState {
    private final long connectionId;
    private final AtomicLong playerId = new AtomicLong(0);
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Integer> inFlightSeqShardIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DeferredEntry> deferred = new ConcurrentHashMap<>();
    private volatile PlayerSession playerSession;
    private final ResponseDedupCache dedupCache;
    private volatile Compression compression = Compression.NONE;

    ConnectionState(long connectionId, DispatcherConfig config) {
        this.connectionId = connectionId;
        this.dedupCache = new ResponseDedupCache(config.dedupMaxEntries(), config.dedupTtlMillis());
    }

    public long connectionId() {
        return connectionId;
    }

    public long playerId() {
        return playerId.get();
    }

    public long sessionEpoch() {
        return sessionEpoch.get();
    }

    public int inFlightCount() {
        return inFlight.get();
    }

    boolean tryAddInFlight(long seq, int max, int shardIndex) {
        if (seq <= 0) {
            return true;
        }
        if (inFlightSeqShardIndex.putIfAbsent(seq, shardIndex) != null) {
            return false;
        }
        int cur = inFlight.incrementAndGet();
        if (cur > max) {
            inFlight.decrementAndGet();
            inFlightSeqShardIndex.remove(seq);
            return false;
        }
        return true;
    }

    int removeInFlight(long seq) {
        if (seq <= 0) {
            return -1;
        }
        Integer shardIndex = inFlightSeqShardIndex.remove(seq);
        if (shardIndex != null) {
            inFlight.decrementAndGet();
            return shardIndex;
        }
        return -1;
    }

    boolean registerDeferred(DeferredEntry entry) {
        if (entry.seq() <= 0) {
            return false;
        }
        return deferred.putIfAbsent(entry.seq(), entry) == null;
    }

    DeferredEntry removeDeferred(long seq) {
        if (seq <= 0) {
            return null;
        }
        return deferred.remove(seq);
    }

    List<DeferredEntry> removeAllDeferred() {
        if (deferred.isEmpty()) {
            return List.of();
        }
        ArrayList<DeferredEntry> list = new ArrayList<>(deferred.size());
        for (DeferredEntry e : deferred.values()) {
            list.add(e);
        }
        deferred.clear();
        return list;
    }

    List<Integer> clearInFlightShardIndices() {
        if (inFlightSeqShardIndex.isEmpty()) {
            inFlight.set(0);
            return List.of();
        }
        ArrayList<Integer> indices = new ArrayList<>(inFlightSeqShardIndex.size());
        for (Integer v : inFlightSeqShardIndex.values()) {
            if (v != null) {
                indices.add(v);
            }
        }
        inFlightSeqShardIndex.clear();
        inFlight.set(0);
        return indices;
    }

    public void attachPlayer(long playerId) {
        this.playerId.set(playerId);
        this.sessionEpoch.incrementAndGet();
    }

    public void detachPlayer() {
        this.playerId.set(0);
        this.sessionEpoch.incrementAndGet();
    }

    PlayerSession playerSession() {
        return playerSession;
    }

    void playerSession(PlayerSession session) {
        this.playerSession = session;
    }

    ResponseDedupCache dedupCache() {
        return dedupCache;
    }

    Compression compression() {
        return compression;
    }

    void compression(Compression compression) {
        this.compression = compression == null ? Compression.NONE : compression;
    }

    record DeferredEntry(
            long seq,
            int msgId,
            long shardKey,
            long expectedEpoch,
            long startNanos,
            CivSpan span
    ) {}
}
