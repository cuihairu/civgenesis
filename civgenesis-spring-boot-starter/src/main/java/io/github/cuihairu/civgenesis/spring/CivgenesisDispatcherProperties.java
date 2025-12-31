package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.dispatcher.runtime.RawPayloadMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.dispatcher")
public class CivgenesisDispatcherProperties {
    private boolean enabled = true;
    private int shards = 64;
    private int maxInFlightPerConnection = 64;
    private int maxInFlightPerShard = 2048;
    private RawPayloadMode rawPayloadMode = RawPayloadMode.RETAIN;
    private boolean closeOnNeedLogin = false;
    private long requestTimeoutMillis = 5_000;
    private long slowRequestMillis = 200;
    private boolean dedupEnabled = true;
    private int dedupMaxEntries = 1024;
    private long dedupTtlMillis = 30_000;
    private int maxBufferedPushCount = 2000;
    private long maxBufferedPushAgeMillis = 60_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getShards() {
        return shards;
    }

    public void setShards(int shards) {
        this.shards = shards;
    }

    public int getMaxInFlightPerConnection() {
        return maxInFlightPerConnection;
    }

    public void setMaxInFlightPerConnection(int maxInFlightPerConnection) {
        this.maxInFlightPerConnection = maxInFlightPerConnection;
    }

    public int getMaxInFlightPerShard() {
        return maxInFlightPerShard;
    }

    public void setMaxInFlightPerShard(int maxInFlightPerShard) {
        this.maxInFlightPerShard = maxInFlightPerShard;
    }

    public RawPayloadMode getRawPayloadMode() {
        return rawPayloadMode;
    }

    public void setRawPayloadMode(RawPayloadMode rawPayloadMode) {
        this.rawPayloadMode = rawPayloadMode;
    }

    public boolean isCloseOnNeedLogin() {
        return closeOnNeedLogin;
    }

    public void setCloseOnNeedLogin(boolean closeOnNeedLogin) {
        this.closeOnNeedLogin = closeOnNeedLogin;
    }

    public long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public long getSlowRequestMillis() {
        return slowRequestMillis;
    }

    public void setSlowRequestMillis(long slowRequestMillis) {
        this.slowRequestMillis = slowRequestMillis;
    }

    public boolean isDedupEnabled() {
        return dedupEnabled;
    }

    public void setDedupEnabled(boolean dedupEnabled) {
        this.dedupEnabled = dedupEnabled;
    }

    public int getDedupMaxEntries() {
        return dedupMaxEntries;
    }

    public void setDedupMaxEntries(int dedupMaxEntries) {
        this.dedupMaxEntries = dedupMaxEntries;
    }

    public long getDedupTtlMillis() {
        return dedupTtlMillis;
    }

    public void setDedupTtlMillis(long dedupTtlMillis) {
        this.dedupTtlMillis = dedupTtlMillis;
    }

    public int getMaxBufferedPushCount() {
        return maxBufferedPushCount;
    }

    public void setMaxBufferedPushCount(int maxBufferedPushCount) {
        this.maxBufferedPushCount = maxBufferedPushCount;
    }

    public long getMaxBufferedPushAgeMillis() {
        return maxBufferedPushAgeMillis;
    }

    public void setMaxBufferedPushAgeMillis(long maxBufferedPushAgeMillis) {
        this.maxBufferedPushAgeMillis = maxBufferedPushAgeMillis;
    }
}
