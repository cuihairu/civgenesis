package io.github.cuihairu.civgenesis.jobs;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class LocalJobScheduler implements JobScheduler {
    private final ScheduledExecutorService executor;

    public LocalJobScheduler(String threadName) {
        String name = Objects.requireNonNullElse(threadName, "civgenesis-job-");
        this.executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(name, 0).factory());
    }

    @Override
    public ScheduledJob scheduleWithFixedDelay(Duration initialDelay, Duration delay, Runnable task) {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        long initialMs = Math.max(0, initialDelay.toMillis());
        long delayMs = Math.max(1, delay.toMillis());
        ScheduledFuture<?> f = executor.scheduleWithFixedDelay(task, initialMs, delayMs, TimeUnit.MILLISECONDS);
        return () -> f.cancel(false);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}

