package io.github.cuihairu.civgenesis.spring.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.observability")
public class CivgenesisObservabilityProperties {
    private Metrics metrics = new Metrics();
    private Prometheus prometheus = new Prometheus();
    private Tracing tracing = new Tracing();

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public void setPrometheus(Prometheus prometheus) {
        this.prometheus = prometheus;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    public static class Metrics {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Prometheus {
        private boolean enabled = false;
        private String host = "0.0.0.0";
        private int port = 9090;
        private String path = "/metrics";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Tracing {
        private boolean enabled = false;
        private String instrumentationName = "civgenesis";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInstrumentationName() {
            return instrumentationName;
        }

        public void setInstrumentationName(String instrumentationName) {
            this.instrumentationName = instrumentationName;
        }
    }
}

