package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OpenTelemetryCivTracerTest {
    @Test
    void startsClosesAndMarksSpansWithoutSdk() {
        OpenTelemetryCivTracer tracer = new OpenTelemetryCivTracer("test");
        OpenTelemetryCivTracer defaultTracer = new OpenTelemetryCivTracer(null);

        CivSpan request = tracer.startRequestSpan(1, 2, 3, 4, 5);
        request.setAttribute("key", "value");
        request.setAttribute("count", 42L);
        request.recordException(new IllegalStateException("boom"));
        request.setError("failed");
        request.close();
        assertDoesNotThrow(request::close);

        CivSpan job = defaultTracer.startJobSpan("demo", 9);
        job.setError("job failed");
        job.recordException(new IllegalArgumentException("bad job"));
        job.close();
        assertDoesNotThrow(job::close);
    }
}
