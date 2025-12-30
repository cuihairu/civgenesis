package io.github.cuihairu.civgenesis.jobs;

import java.time.Duration;

public interface JobScheduler extends AutoCloseable {
    ScheduledJob scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable task);

    @Override
    void close();
}

