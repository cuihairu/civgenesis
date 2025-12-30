package io.github.cuihairu.civgenesis.system;

import java.util.UUID;

public final class SystemServerEpoch {
    private final String value;

    public SystemServerEpoch() {
        this(UUID.randomUUID().toString());
    }

    public SystemServerEpoch(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}

