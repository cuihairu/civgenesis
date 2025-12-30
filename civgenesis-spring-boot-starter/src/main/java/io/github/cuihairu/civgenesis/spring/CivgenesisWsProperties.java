package io.github.cuihairu.civgenesis.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.ws")
public class CivgenesisWsProperties {
    private boolean enabled = false;
    private int port = 8888;
    private String path = "/";
    private int maxFrameBytes = 1024 * 1024;
    private int idleTimeoutSeconds = 30;
    private boolean pingBeforeClose = true;
    private int pingTimeoutMillis = 3000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    public void setMaxFrameBytes(int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public boolean isPingBeforeClose() {
        return pingBeforeClose;
    }

    public void setPingBeforeClose(boolean pingBeforeClose) {
        this.pingBeforeClose = pingBeforeClose;
    }

    public int getPingTimeoutMillis() {
        return pingTimeoutMillis;
    }

    public void setPingTimeoutMillis(int pingTimeoutMillis) {
        this.pingTimeoutMillis = pingTimeoutMillis;
    }
}

