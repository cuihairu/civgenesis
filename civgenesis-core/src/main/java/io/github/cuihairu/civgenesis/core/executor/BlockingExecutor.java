package io.github.cuihairu.civgenesis.core.executor;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BlockingExecutor implements Closeable {
    private final ExecutorService executor;

    public BlockingExecutor() {
        this(Executors.newVirtualThreadPerTaskExecutor());
    }

    public BlockingExecutor(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.close();
    }
}

