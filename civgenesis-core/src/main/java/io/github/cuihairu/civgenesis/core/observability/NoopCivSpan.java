package io.github.cuihairu.civgenesis.core.observability;

enum NoopCivSpan implements CivSpan {
    INSTANCE;

    @Override
    public void close() {
    }
}

