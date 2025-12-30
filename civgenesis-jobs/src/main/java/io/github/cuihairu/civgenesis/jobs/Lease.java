package io.github.cuihairu.civgenesis.jobs;

public interface Lease extends AutoCloseable {
    long fencingToken();

    @Override
    void close();
}

