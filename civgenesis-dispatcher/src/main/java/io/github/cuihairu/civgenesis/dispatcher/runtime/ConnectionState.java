package io.github.cuihairu.civgenesis.dispatcher.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ConnectionState {
    private final long connectionId;
    private final AtomicLong playerId = new AtomicLong(0);
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, Boolean> inFlightSeq = new ConcurrentHashMap<>();
    private volatile PlayerSession playerSession;
    private final ResponseDedupCache dedupCache;

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

    boolean tryAddInFlight(long seq, int max) {
        if (seq <= 0) {
            return true;
        }
        int cur = inFlight.incrementAndGet();
        if (cur > max) {
            inFlight.decrementAndGet();
            return false;
        }
        inFlightSeq.put(seq, Boolean.TRUE);
        return true;
    }

    boolean removeInFlight(long seq) {
        if (seq <= 0) {
            return false;
        }
        if (inFlightSeq.remove(seq) != null) {
            inFlight.decrementAndGet();
            return true;
        }
        return false;
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
}
