package io.github.cuihairu.civgenesis.scheduler;

import java.time.Duration;

public interface ShardScheduler extends AutoCloseable {
    ScheduledTask schedule(long shardKey, Duration delay, Runnable task);

    @Override
    void close();
}

