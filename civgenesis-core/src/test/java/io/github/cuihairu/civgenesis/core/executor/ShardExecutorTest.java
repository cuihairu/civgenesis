package io.github.cuihairu.civgenesis.core.executor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardExecutorTest {
    @Test
    void rejectsInvalidConstructionAndTask() {
        assertThrows(IllegalArgumentException.class, () -> new ShardExecutor(0, "bad"));
        try (ShardExecutor ignored = new ShardExecutor(1, null)) {
            assertThrows(NullPointerException.class, () -> ignored.execute(1, null));
        }
    }

    @Test
    void routesToConsistentShardAndExecutesTask() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        try (ShardExecutor executor = new ShardExecutor(3, "test")) {
            assertEquals(3, executor.shards());
            assertEquals(executor.shardIndex(42L), executor.shardIndex(42L));
            assertEquals(0, executor.shardIndex(0L));
            assertEquals(2, executor.shardIndex(3L));
            executor.execute(42L, executed::countDown);
            assertTrue(executed.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeToleratesInterruptedThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ShardExecutor executor = new ShardExecutor(1, "interrupt");
        executor.execute(1L, () -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread.currentThread().interrupt();
        try {
            executor.close(); // awaitTermination throws -> the catch re-interrupts
        } finally {
            release.countDown();
            Thread.interrupted(); // clear the flag so other tests are unaffected
        }
    }
}
