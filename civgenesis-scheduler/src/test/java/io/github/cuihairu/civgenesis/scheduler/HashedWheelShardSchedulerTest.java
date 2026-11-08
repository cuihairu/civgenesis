package io.github.cuihairu.civgenesis.scheduler;

import io.github.cuihairu.civgenesis.core.executor.ShardExecutor;
import io.netty.util.HashedWheelTimer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashedWheelShardSchedulerTest {
    @Test
    void schedulesZeroDelayIntoShardAndSupportsCancellation() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        HashedWheelTimer timer = new HashedWheelTimer();
        try (ShardExecutor shards = new ShardExecutor(2, "test-shard");
             HashedWheelShardScheduler scheduler = new HashedWheelShardScheduler(timer, shards)) {
            scheduler.schedule(1, Duration.ZERO, executed::countDown);
            assertTrue(executed.await(1, TimeUnit.SECONDS));
            assertThrows(NullPointerException.class, () -> scheduler.schedule(1, null, () -> {}));
            assertThrows(NullPointerException.class, () -> scheduler.schedule(1, Duration.ZERO, null));
            assertTrue(scheduler.schedule(1, Duration.ofHours(1), () -> {}).cancel());
        } finally {
            timer.stop();
        }
    }
}
