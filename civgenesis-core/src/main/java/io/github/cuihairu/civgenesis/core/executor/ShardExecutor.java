package io.github.cuihairu.civgenesis.core.executor;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ShardExecutor implements Closeable {
    private final ExecutorService[] executors;
    private final int shards;

    public ShardExecutor(int shards, String threadNamePrefix) {
        if (shards <= 0) {
            throw new IllegalArgumentException("shards must be > 0");
        }
        this.shards = shards;
        this.executors = new ExecutorService[shards];
        String prefix = Objects.requireNonNullElse(threadNamePrefix, "civgenesis-shard-");
        ThreadFactory threadFactory = Thread.ofPlatform().name(prefix, 0).factory();
        for (int i = 0; i < shards; i++) {
            executors[i] = Executors.newSingleThreadExecutor(threadFactory);
        }
    }

    public int shards() {
        return shards;
    }

    public int shardIndex(long key) {
        long h = mix64(key);
        return (int) (Math.floorMod(h, shards));
    }

    public void execute(long shardKey, Runnable task) {
        Objects.requireNonNull(task, "task");
        executors[shardIndex(shardKey)].execute(task);
    }

    @Override
    public void close() {
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }
        for (ExecutorService executor : executors) {
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}

