package io.github.cuihairu.civgenesis.core.executor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingExecutorTest {

    @Test
    void submitsAndReturnsTaskResult() throws Exception {
        try (BlockingExecutor executor = new BlockingExecutor()) {
            CompletableFuture<Integer> result = executor.submit(() -> 40 + 2);
            assertEquals(42, result.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void wrapsTaskFailureAndClosesIdempotently() throws Exception {
        try (BlockingExecutor executor = new BlockingExecutor(
                java.util.concurrent.Executors.newFixedThreadPool(1))) {
            CompletableFuture<Integer> failing = executor.submit(() -> {
                throw new IllegalStateException("boom");
            });
            java.util.concurrent.ExecutionException wrapped = assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> failing.get(5, TimeUnit.SECONDS));
            // the future wraps the task's RuntimeException, whose cause is the original failure
            assertTrue(wrapped.getCause() instanceof RuntimeException);
            assertTrue(wrapped.getCause().getCause() instanceof IllegalStateException);
            // a second close on the same executor service is tolerated by the JVM
            executor.close();
        }
    }

    @Test
    void rejectsNullExecutor() {
        assertThrows(NullPointerException.class, () -> new BlockingExecutor(null));
    }

    @Test
    void completesNullReturningTask() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        try (BlockingExecutor executor = new BlockingExecutor()) {
            CompletableFuture<Void> task = executor.submit(() -> {
                done.countDown();
                return null;
            });
            assertNull(task.get(5, TimeUnit.SECONDS));
            assertTrue(done.await(1, TimeUnit.SECONDS));
        }
    }
}
