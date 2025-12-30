package io.github.cuihairu.civgenesis.core.observability;

public interface CivSpan extends AutoCloseable {
    default void recordException(Throwable t) {}

    default void setAttribute(String key, String value) {}

    default void setAttribute(String key, long value) {}

    default void setError(String message) {}

    @Override
    void close();

    static CivSpan noop() {
        return NoopCivSpan.INSTANCE;
    }
}

