package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.dispatcher.runtime.RawPayloadMode;
import io.github.cuihairu.civgenesis.spring.jobs.CivgenesisJobsProperties;
import io.github.cuihairu.civgenesis.spring.observability.CivgenesisObservabilityProperties;
import io.github.cuihairu.civgenesis.spring.registry.CivgenesisNacosProperties;
import io.github.cuihairu.civgenesis.spring.system.CivgenesisSystemProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivgenesisPropertiesTest {
    @Test
    void dispatcherPropertiesRoundTrip() {
        CivgenesisDispatcherProperties p = new CivgenesisDispatcherProperties();
        assertTrue(p.isEnabled());
        assertEquals(64, p.getShards());
        assertEquals(RawPayloadMode.RETAIN, p.getRawPayloadMode());

        p.setEnabled(false);
        p.setShards(3);
        p.setMaxInFlightPerConnection(11);
        p.setMaxInFlightPerShard(12);
        p.setRawPayloadMode(RawPayloadMode.COPY);
        p.setCloseOnNeedLogin(true);
        p.setRequestTimeoutMillis(13);
        p.setSlowRequestMillis(14);
        p.setDedupEnabled(false);
        p.setDedupMaxEntries(15);
        p.setDedupTtlMillis(16);
        p.setMaxBufferedPushCount(17);
        p.setMaxBufferedPushAgeMillis(18);

        assertFalse(p.isEnabled());
        assertEquals(3, p.getShards());
        assertEquals(11, p.getMaxInFlightPerConnection());
        assertEquals(12, p.getMaxInFlightPerShard());
        assertEquals(RawPayloadMode.COPY, p.getRawPayloadMode());
        assertTrue(p.isCloseOnNeedLogin());
        assertEquals(13, p.getRequestTimeoutMillis());
        assertEquals(14, p.getSlowRequestMillis());
        assertFalse(p.isDedupEnabled());
        assertEquals(15, p.getDedupMaxEntries());
        assertEquals(16, p.getDedupTtlMillis());
        assertEquals(17, p.getMaxBufferedPushCount());
        assertEquals(18, p.getMaxBufferedPushAgeMillis());
    }

    @Test
    void tcpPropertiesRoundTrip() {
        CivgenesisTcpProperties p = new CivgenesisTcpProperties();
        assertFalse(p.isEnabled());
        assertEquals(9999, p.getPort());
        assertEquals("0.0.0.0", p.getHost());

        p.setEnabled(true);
        p.setBossThreads(2);
        p.setWorkerThreads(4);
        p.setHost("127.0.0.1");
        p.setPort(1);
        p.setSoBacklog(64);
        p.setRecvBufBytes(128);
        p.setSendBufBytes(256);
        p.setPooledAllocator(false);
        p.setMaxFrameBytes(512);
        p.setIdleTimeoutSeconds(9);

        assertTrue(p.isEnabled());
        assertEquals(2, p.getBossThreads());
        assertEquals(4, p.getWorkerThreads());
        assertEquals("127.0.0.1", p.getHost());
        assertEquals(1, p.getPort());
        assertEquals(64, p.getSoBacklog());
        assertEquals(128, p.getRecvBufBytes());
        assertEquals(256, p.getSendBufBytes());
        assertFalse(p.isPooledAllocator());
        assertEquals(512, p.getMaxFrameBytes());
        assertEquals(9, p.getIdleTimeoutSeconds());
    }

    @Test
    void wsPropertiesRoundTrip() {
        CivgenesisWsProperties p = new CivgenesisWsProperties();
        assertFalse(p.isEnabled());
        assertEquals(8888, p.getPort());
        assertEquals("/", p.getPath());
        assertTrue(p.isPingBeforeClose());

        p.setEnabled(true);
        p.setBossThreads(2);
        p.setWorkerThreads(4);
        p.setPort(1);
        p.setPath("/ws");
        p.setSoBacklog(64);
        p.setRecvBufBytes(128);
        p.setSendBufBytes(256);
        p.setPooledAllocator(false);
        p.setMaxFrameBytes(512);
        p.setIdleTimeoutSeconds(9);
        p.setPingBeforeClose(false);
        p.setPingTimeoutMillis(40);

        assertTrue(p.isEnabled());
        assertEquals(2, p.getBossThreads());
        assertEquals(4, p.getWorkerThreads());
        assertEquals(1, p.getPort());
        assertEquals("/ws", p.getPath());
        assertEquals(64, p.getSoBacklog());
        assertEquals(128, p.getRecvBufBytes());
        assertEquals(256, p.getSendBufBytes());
        assertFalse(p.isPooledAllocator());
        assertEquals(512, p.getMaxFrameBytes());
        assertEquals(9, p.getIdleTimeoutSeconds());
        assertFalse(p.isPingBeforeClose());
        assertEquals(40, p.getPingTimeoutMillis());
    }

    @Test
    void jobsPropertiesRoundTrip() {
        CivgenesisJobsProperties p = new CivgenesisJobsProperties();
        assertFalse(p.isEnabled());
        assertEquals("civgenesis-job-", p.getThreadName());
        p.setEnabled(true);
        p.setThreadName("j-");
        assertTrue(p.isEnabled());
        assertEquals("j-", p.getThreadName());
    }

    @Test
    void nacosPropertiesRoundTrip() {
        CivgenesisNacosProperties p = new CivgenesisNacosProperties();
        assertTrue(p.isEnabled());
        assertEquals("127.0.0.1:8848", p.getServerAddr());
        assertEquals("DEFAULT_GROUP", p.getGroup());
        assertEquals("", p.getNamespace());
        assertEquals("", p.getUsername());
        assertEquals("", p.getPassword());

        p.setEnabled(false);
        p.setServerAddr("nacos:8848");
        p.setNamespace("ns");
        p.setUsername("u");
        p.setPassword("p");
        p.setGroup("CG");
        assertFalse(p.isEnabled());
        assertEquals("nacos:8848", p.getServerAddr());
        assertEquals("ns", p.getNamespace());
        assertEquals("u", p.getUsername());
        assertEquals("p", p.getPassword());
        assertEquals("CG", p.getGroup());
    }

    @Test
    void systemPropertiesRoundTrip() {
        CivgenesisSystemProperties p = new CivgenesisSystemProperties();
        assertTrue(p.isEnabled());
        assertFalse(p.isGzipEnabled());
        p.setEnabled(false);
        p.setGzipEnabled(true);
        assertFalse(p.isEnabled());
        assertTrue(p.isGzipEnabled());
    }

    @Test
    void observabilityPropertiesRoundTrip() {
        CivgenesisObservabilityProperties p = new CivgenesisObservabilityProperties();
        assertFalse(p.getMetrics().isEnabled());
        assertFalse(p.getPrometheus().isEnabled());
        assertEquals(9090, p.getPrometheus().getPort());
        assertEquals("0.0.0.0", p.getPrometheus().getHost());
        assertEquals("/metrics", p.getPrometheus().getPath());
        assertFalse(p.getTracing().isEnabled());
        assertEquals("civgenesis", p.getTracing().getInstrumentationName());

        CivgenesisObservabilityProperties.Metrics metrics = new CivgenesisObservabilityProperties.Metrics();
        metrics.setEnabled(true);
        p.setMetrics(metrics);
        CivgenesisObservabilityProperties.Prometheus prometheus = new CivgenesisObservabilityProperties.Prometheus();
        prometheus.setEnabled(true);
        prometheus.setHost("localhost");
        prometheus.setPort(1);
        prometheus.setPath("/m");
        p.setPrometheus(prometheus);
        CivgenesisObservabilityProperties.Tracing tracing = new CivgenesisObservabilityProperties.Tracing();
        tracing.setEnabled(true);
        tracing.setInstrumentationName("obs");
        p.setTracing(tracing);

        assertTrue(p.getMetrics().isEnabled());
        assertTrue(p.getPrometheus().isEnabled());
        assertEquals("localhost", p.getPrometheus().getHost());
        assertEquals(1, p.getPrometheus().getPort());
        assertEquals("/m", p.getPrometheus().getPath());
        assertTrue(p.getTracing().isEnabled());
        assertEquals("obs", p.getTracing().getInstrumentationName());
    }
}
