package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.core.observability.CivMetrics;
import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class MicrometerCivMetrics implements CivMetrics {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Timer> requestTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();

    MicrometerCivMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        registry.gauge("civgenesis_dispatch_in_flight", inFlight);
    }

    @Override
    public void onConnectionOpen(String transport) {
        AtomicInteger gauge = connections.computeIfAbsent(transport, t -> {
            AtomicInteger g = new AtomicInteger(0);
            registry.gauge("civgenesis_transport_connections", java.util.List.of(io.micrometer.core.instrument.Tag.of("transport", t)), g);
            return g;
        });
        gauge.incrementAndGet();
        Counter.builder("civgenesis_transport_connections_total")
                .tag("transport", transport)
                .tag("event", "open")
                .register(registry)
                .increment();
    }

    @Override
    public void onConnectionClose(String transport) {
        AtomicInteger gauge = connections.computeIfAbsent(transport, t -> {
            AtomicInteger g = new AtomicInteger(0);
            registry.gauge("civgenesis_transport_connections", java.util.List.of(io.micrometer.core.instrument.Tag.of("transport", t)), g);
            return g;
        });
        gauge.decrementAndGet();
        Counter.builder("civgenesis_transport_connections_total")
                .tag("transport", transport)
                .tag("event", "close")
                .register(registry)
                .increment();
    }

    @Override
    public void onFrameIn(String transport, FrameType type, int msgId, int bytes) {
        Counter.builder("civgenesis_transport_frames_total")
                .tag("transport", transport)
                .tag("direction", "in")
                .tag("type", type.name())
                .register(registry)
                .increment();
        DistributionSummary.builder("civgenesis_transport_frame_bytes")
                .tag("transport", transport)
                .tag("direction", "in")
                .tag("type", type.name())
                .register(registry)
                .record(bytes);
    }

    @Override
    public void onFrameOut(String transport, FrameType type, int msgId, int bytes) {
        Counter.builder("civgenesis_transport_frames_total")
                .tag("transport", transport)
                .tag("direction", "out")
                .tag("type", type.name())
                .register(registry)
                .increment();
        DistributionSummary.builder("civgenesis_transport_frame_bytes")
                .tag("transport", transport)
                .tag("direction", "out")
                .tag("type", type.name())
                .register(registry)
                .record(bytes);
    }

    @Override
    public void onInFlightInc() {
        inFlight.incrementAndGet();
    }

    @Override
    public void onInFlightDec() {
        inFlight.decrementAndGet();
    }

    @Override
    public void onRequestComplete(int msgId, boolean success, int errorCode, long durationNanos) {
        String status = success ? "ok" : "error";
        String key = msgId + "|" + status + "|" + errorCode;

        Timer timer = requestTimers.computeIfAbsent(key, k -> Timer.builder("civgenesis_dispatch_request_seconds")
                .tag("msg_id", String.valueOf(msgId))
                .tag("status", status)
                .tag("error_code", String.valueOf(errorCode))
                .publishPercentileHistogram()
                .register(registry));
        timer.record(durationNanos, TimeUnit.NANOSECONDS);

        Counter counter = requestCounters.computeIfAbsent(key, k -> Counter.builder("civgenesis_dispatch_requests_total")
                .tag("msg_id", String.valueOf(msgId))
                .tag("status", status)
                .tag("error_code", String.valueOf(errorCode))
                .register(registry));
        counter.increment();
    }

    @Override
    public void onJobRun(String jobName, boolean success, long durationNanos) {
        String status = success ? "ok" : "error";
        Timer.builder("civgenesis_job_seconds")
                .tag("job", jobName)
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        Counter.builder("civgenesis_job_total")
                .tag("job", jobName)
                .tag("status", status)
                .register(registry)
                .increment();
    }
}
