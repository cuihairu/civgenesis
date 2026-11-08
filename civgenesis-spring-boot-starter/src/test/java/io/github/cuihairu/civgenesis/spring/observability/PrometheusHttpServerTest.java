package io.github.cuihairu.civgenesis.spring.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusHttpServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private PrometheusHttpServer server;

    @Test
    void servesMetricsAndValidatesRoutes() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter("test_total").increment();
        server = new PrometheusHttpServer("127.0.0.1", 0, "/custom", registry);
        server.start();
        int port = ((java.net.InetSocketAddress) server.localAddress()).getPort();

        HttpResponse<String> metrics = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/custom")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("test_total"));

        HttpResponse<String> missing = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/missing")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missing.statusCode());

        HttpResponse<String> method = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/custom")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, method.statusCode());

        server.stop();
        assertTrue(server.localAddress() == null);
        assertTrue(metrics.headers().firstValue("content-type").orElse("").startsWith("text/plain"));
    }

    @Test
    void stopIsIdempotentAndStartValidatesRegistry() {
        PrometheusHttpServer empty = new PrometheusHttpServer(null, 0, null, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        empty.stop();
        assertThrows(NullPointerException.class, () -> new PrometheusHttpServer("127.0.0.1", 0, "/metrics", (PrometheusMeterRegistry) null));
        assertTrue(new SimpleMeterRegistry() instanceof io.micrometer.core.instrument.MeterRegistry);
    }
}
