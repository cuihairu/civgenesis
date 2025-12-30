package io.github.cuihairu.civgenesis.dispatcher.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class PlayerSession {
    private final long playerId;
    private final int maxBufferedPushCount;
    private final long maxBufferedPushAgeMillis;

    private long nextPushId = 1;
    private long lastAckedPushId = 0;
    private final Deque<PushEntry> pushBuffer = new ArrayDeque<>();

    PlayerSession(long playerId, int maxBufferedPushCount, long maxBufferedPushAgeMillis) {
        this.playerId = playerId;
        this.maxBufferedPushCount = Math.max(0, maxBufferedPushCount);
        this.maxBufferedPushAgeMillis = Math.max(0, maxBufferedPushAgeMillis);
    }

    long playerId() {
        return playerId;
    }

    synchronized long nextPushId() {
        return nextPushId++;
    }

    synchronized void recordPush(PushEntry entry) {
        pushBuffer.addLast(entry);
        evict(entry.tsMillis());
    }

    synchronized void ack(long pushId) {
        if (pushId <= lastAckedPushId) {
            return;
        }
        lastAckedPushId = pushId;
        while (!pushBuffer.isEmpty() && pushBuffer.peekFirst().pushId() <= pushId) {
            pushBuffer.removeFirst();
        }
    }

    synchronized ReplayPlan planReplay(long lastAppliedPushId, long nowMillis) {
        evict(nowMillis);
        long min = pushBuffer.isEmpty() ? 0 : pushBuffer.peekFirst().pushId();
        long max = pushBuffer.isEmpty() ? 0 : pushBuffer.peekLast().pushId();

        if (lastAppliedPushId == 0) {
            return new ReplayPlan(true, min, max, listFrom(0));
        }
        if (pushBuffer.isEmpty()) {
            return new ReplayPlan(false, 0, 0, List.of());
        }
        if (lastAppliedPushId < min - 1) {
            return new ReplayPlan(false, min, max, List.of());
        }
        if (lastAppliedPushId > max) {
            return new ReplayPlan(false, min, max, List.of());
        }
        return new ReplayPlan(true, min, max, listFrom(lastAppliedPushId));
    }

    private List<PushEntry> listFrom(long lastAppliedPushId) {
        List<PushEntry> list = new ArrayList<>();
        for (PushEntry e : pushBuffer) {
            if (e.pushId() > lastAppliedPushId) {
                list.add(e);
            }
        }
        return list;
    }

    private void evict(long nowMillis) {
        if (maxBufferedPushCount > 0) {
            while (pushBuffer.size() > maxBufferedPushCount) {
                pushBuffer.removeFirst();
            }
        }
        if (maxBufferedPushAgeMillis > 0) {
            while (!pushBuffer.isEmpty() && nowMillis - pushBuffer.peekFirst().tsMillis() > maxBufferedPushAgeMillis) {
                pushBuffer.removeFirst();
            }
        }
        if (lastAckedPushId > 0) {
            while (!pushBuffer.isEmpty() && pushBuffer.peekFirst().pushId() <= lastAckedPushId) {
                pushBuffer.removeFirst();
            }
        }
    }

    record PushEntry(long pushId, int msgId, long flags, long tsMillis, byte[] payloadBytes) {}

    record ReplayPlan(boolean ok, long minBufferedPushId, long maxBufferedPushId, List<PushEntry> toReplay) {}
}

