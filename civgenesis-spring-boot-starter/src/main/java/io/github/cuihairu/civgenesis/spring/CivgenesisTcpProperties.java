package io.github.cuihairu.civgenesis.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.tcp")
public class CivgenesisTcpProperties {
    private boolean enabled = false;
    private int bossThreads = 1;
    private int workerThreads = 0;
    private String host = "0.0.0.0";
    private int port = 9999;
    private int soBacklog = 1024;
    private int recvBufBytes = 0;
    private int sendBufBytes = 0;
    private boolean pooledAllocator = true;
    private int maxFrameBytes = 1024 * 1024;
    private int idleTimeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
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

    public int getSoBacklog() {
        return soBacklog;
    }

    public void setSoBacklog(int soBacklog) {
        this.soBacklog = soBacklog;
    }

    public int getRecvBufBytes() {
        return recvBufBytes;
    }

    public void setRecvBufBytes(int recvBufBytes) {
        this.recvBufBytes = recvBufBytes;
    }

    public int getSendBufBytes() {
        return sendBufBytes;
    }

    public void setSendBufBytes(int sendBufBytes) {
        this.sendBufBytes = sendBufBytes;
    }

    public boolean isPooledAllocator() {
        return pooledAllocator;
    }

    public void setPooledAllocator(boolean pooledAllocator) {
        this.pooledAllocator = pooledAllocator;
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
}

