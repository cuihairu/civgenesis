package io.github.cuihairu.civgenesis.scheduler;

import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class HashedWheelShardScheduler implements ShardScheduler {
    private final HashedWheelTimer timer;
    private final ShardExecutor shardExecutor;

    public HashedWheelShardScheduler(HashedWheelTimer timer, ShardExecutor shardExecutor) {
        this.timer = Objects.requireNonNull(timer, "timer");
        this.shardExecutor = Objects.requireNonNull(shardExecutor, "shardExecutor");
    }

    @Override
    public ScheduledTask schedule(long shardKey, Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        long delayMillis = Math.max(0, delay.toMillis());
        Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                shardExecutor.execute(shardKey, task);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        return timeout::cancel;
    }

    @Override
    public void close() {
        timer.stop();
    }
}

