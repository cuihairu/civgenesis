package io.github.cuihairu.civgenesis.dispatcher.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ResponseDedupCache {
    private final int maxEntries;
    private final long ttlMillis;
    private final LinkedHashMap<Long, Entry> map;

    ResponseDedupCache(int maxEntries, long ttlMillis) {
        this.maxEntries = Math.max(0, maxEntries);
        this.ttlMillis = Math.max(0, ttlMillis);
        this.map = new LinkedHashMap<>(16, 0.75f, false);
    }

    Entry get(long seq, long nowMillis) {
        if (maxEntries <= 0 || ttlMillis <= 0) {
            return null;
        }
        Entry e;
        synchronized (map) {
            e = map.get(seq);
        }
        if (e == null) {
            return null;
        }
        if (nowMillis - e.storedAtMillis > ttlMillis) {
            synchronized (map) {
                map.remove(seq);
            }
            return null;
        }
        return e;
    }

    void put(long seq, int msgId, long flags, byte[] payloadBytes, long nowMillis) {
        if (maxEntries <= 0 || ttlMillis <= 0) {
            return;
        }
        Objects.requireNonNull(payloadBytes, "payloadBytes");
        synchronized (map) {
            map.put(seq, new Entry(seq, msgId, flags, payloadBytes, nowMillis));
            while (map.size() > maxEntries) {
                Map.Entry<Long, Entry> eldest = map.entrySet().iterator().next();
                map.remove(eldest.getKey());
            }
        }
    }

    record Entry(long seq, int msgId, long flags, byte[] payloadBytes, long storedAtMillis) {}
}

