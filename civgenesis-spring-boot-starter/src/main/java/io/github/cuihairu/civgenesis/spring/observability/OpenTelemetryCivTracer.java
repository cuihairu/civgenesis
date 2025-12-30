package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.core.observability.CivSpan;
import io.github.cuihairu.civgenesis.core.observability.CivTracer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class OpenTelemetryCivTracer implements CivTracer {
    private final Tracer tracer;

    OpenTelemetryCivTracer(String instrumentationName) {
        String name = Objects.requireNonNullElse(instrumentationName, "civgenesis");
        this.tracer = GlobalOpenTelemetry.getTracer(name);
    }

    @Override
    public CivSpan startRequestSpan(long connectionId, long playerId, long sessionEpoch, int msgId, long seq) {
        Span span = tracer.spanBuilder("civgenesis.req")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        span.setAttribute("cg.connection_id", connectionId);
        span.setAttribute("cg.player_id", playerId);
        span.setAttribute("cg.session_epoch", sessionEpoch);
        span.setAttribute("cg.msg_id", msgId);
        span.setAttribute("cg.seq", seq);
        Scope scope = span.makeCurrent();
        return new OtelSpan(span, scope);
    }

    @Override
    public CivSpan startJobSpan(String jobName, long runId) {
        Span span = tracer.spanBuilder("civgenesis.job")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.setAttribute("cg.job_name", jobName);
        span.setAttribute("cg.job_run_id", runId);
        Scope scope = span.makeCurrent();
        return new OtelSpan(span, scope);
    }

    private static final class OtelSpan implements CivSpan {
        private final Span span;
        private final Scope scope;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private OtelSpan(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }

        @Override
        public void recordException(Throwable t) {
            span.recordException(t);
        }

        @Override
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }

        @Override
        public void setAttribute(String key, long value) {
            span.setAttribute(key, value);
        }

        @Override
        public void setError(String message) {
            span.setStatus(StatusCode.ERROR, message);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                scope.close();
            } finally {
                span.end();
            }
        }
    }
}
