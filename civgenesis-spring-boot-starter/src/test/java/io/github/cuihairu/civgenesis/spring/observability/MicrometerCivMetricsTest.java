package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.core.protocol.FrameType;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrometerCivMetricsTest {
    @Test
    void recordsConnectionsFramesRequestsAndJobs() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerCivMetrics metrics = new MicrometerCivMetrics(registry);

        metrics.onConnectionOpen("tcp");
        metrics.onConnectionOpen("tcp");
        metrics.onConnectionOpen("ws");
        metrics.onConnectionClose("tcp");
        assertEquals(1, gauge(registry, "civgenesis_transport_connections", "tcp"));
        assertEquals(1, gauge(registry, "civgenesis_transport_connections", "ws"));
        assertEquals(2, counter(registry, "civgenesis_transport_connections_total", "transport", "tcp", "event", "open"));
        assertEquals(1, counter(registry, "civgenesis_transport_connections_total", "transport", "tcp", "event", "close"));

        metrics.onFrameIn("tcp", FrameType.REQ, 1, 128);
        metrics.onFrameOut("tcp", FrameType.RESP, 1, 256);
        assertEquals(1, counter(registry, "civgenesis_transport_frames_total", "transport", "tcp", "direction", "in", "type", "REQ"));
        assertEquals(1, counter(registry, "civgenesis_transport_frames_total", "transport", "tcp", "direction", "out", "type", "RESP"));
        assertEquals(128, registry.get("civgenesis_transport_frame_bytes").tag("direction", "in").summary().totalAmount() > 0 ? 128 : 0);

        metrics.onInFlightInc();
        metrics.onInFlightInc();
        assertEquals(2, registry.get("civgenesis_dispatch_in_flight").gauge().value(), 0.0001);
        metrics.onInFlightDec();
        metrics.onInFlightDec();
        assertEquals(0, registry.get("civgenesis_dispatch_in_flight").gauge().value(), 0.0001);

        metrics.onRequestComplete(7, true, 0, 1_000);
        metrics.onRequestComplete(7, true, 0, 2_000);
        metrics.onRequestComplete(7, false, 500, 3_000);
        assertEquals(2, registry.get("civgenesis_dispatch_request_seconds").tag("msg_id", "7").tag("status", "ok").timer().count());
        assertEquals(1, registry.get("civgenesis_dispatch_request_seconds").tag("status", "error").tag("error_code", "500").timer().count());
        assertEquals(2, registry.get("civgenesis_dispatch_requests_total").tag("status", "ok").tag("error_code", "0").counter().count());

        metrics.onJobRun("demo", true, 5_000);
        metrics.onJobRun("demo", false, 6_000);
        assertEquals(1, registry.get("civgenesis_job_seconds").tag("job", "demo").tag("status", "ok").timer().count());
        assertEquals(1, registry.get("civgenesis_job_total").tag("job", "demo").tag("status", "error").counter().count());
    }

    @Test
    void closeForUnknownTransportCreatesGaugeBelowZero() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerCivMetrics metrics = new MicrometerCivMetrics(registry);
        metrics.onConnectionClose("udp");
        assertEquals(-1, registry.get("civgenesis_transport_connections").tag("transport", "udp").gauge().value(), 0.0001);
        assertEquals(1, registry.get("civgenesis_transport_connections_total").tag("transport", "udp").tag("event", "close").counter().count());
    }

    @Test
    void nullRegistryIsRejected() {
        assertThrows(NullPointerException.class, () -> new MicrometerCivMetrics(null));
    }

    private static double gauge(PrometheusMeterRegistry registry, String name, String transport) {
        return registry.get(name).tag("transport", transport).gauge().value();
    }

    private static double counter(PrometheusMeterRegistry registry, String name, String... tags) {
        return registry.get(name).tags(tags).counter().count();
    }
}
