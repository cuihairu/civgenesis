package io.github.cuihairu.civgenesis.core.observability;

import org.junit.jupiter.api.Test;

class CivSpanNoopTest {
    @Test
    void noopSpanToleratesEveryCall() {
        CivSpan span = CivSpan.noop();
        span.recordException(new IllegalStateException("ignored"));
        span.setAttribute("key", "value");
        span.setAttribute("key", 1L);
        span.setError("error");
        span.close();
    }
}
