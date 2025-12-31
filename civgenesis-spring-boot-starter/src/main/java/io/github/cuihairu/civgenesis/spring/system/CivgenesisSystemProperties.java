package io.github.cuihairu.civgenesis.spring.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.system")
public class CivgenesisSystemProperties {
    private boolean enabled = true;
    private boolean gzipEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGzipEnabled() {
        return gzipEnabled;
    }

    public void setGzipEnabled(boolean gzipEnabled) {
        this.gzipEnabled = gzipEnabled;
    }
}
