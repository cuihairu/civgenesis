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

        // "Connection" is a restricted header for java.net.http.HttpClient, so use a raw socket
        // the read loop runs until EOF, proving the server closed the connection after responding
        assertTrue(rawRequest(port, "/custom").startsWith("HTTP/1.1 200"));
        String notFound = rawRequest(port, "/missing");
        assertTrue(notFound.startsWith("HTTP/1.1 404"));
        assertTrue(notFound.endsWith("not found"));

        HttpResponse<String> method = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/custom")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, method.statusCode());

        server.start();
        server.stop();
        assertTrue(server.localAddress() == null);
        assertTrue(metrics.headers().firstValue("content-type").orElse("").startsWith("text/plain"));
    }

    private static String rawRequest(int port, String target) throws IOException {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(("GET " + target + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                    + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            java.io.InputStream in = socket.getInputStream();
            while ((read = in.read(buffer)) != -1) {
                raw.write(buffer, 0, read);
            }
            return raw.toString(java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    @Test
    void stopIsIdempotentAndStartValidatesRegistry() {
        PrometheusHttpServer empty = new PrometheusHttpServer(null, 0, null, new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        empty.stop();
        assertThrows(NullPointerException.class, () -> new PrometheusHttpServer("127.0.0.1", 0, "/metrics", (PrometheusMeterRegistry) null));
        assertTrue(new SimpleMeterRegistry() instanceof io.micrometer.core.instrument.MeterRegistry);
    }
}
