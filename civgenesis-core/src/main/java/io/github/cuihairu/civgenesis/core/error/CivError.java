package io.github.cuihairu.civgenesis.core.error;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class CivError {
    private final int code;
    private final String message;
    private final boolean retryable;
    private final Map<String, String> detail;

    public CivError(int code, String message, boolean retryable, Map<String, String> detail) {
        this.code = code;
        this.message = Objects.requireNonNullElse(message, "");
        this.retryable = retryable;
        this.detail = detail == null ? Collections.emptyMap() : Collections.unmodifiableMap(detail);
    }

    public static CivError of(int code, String message, boolean retryable) {
        return new CivError(code, message, retryable, null);
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }

    public Map<String, String> detail() {
        return detail;
    }
}

