package io.github.cuihairu.civgenesis.spring;

import io.github.cuihairu.civgenesis.dispatcher.runtime.RawPayloadMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civgenesis.dispatcher")
public class CivgenesisDispatcherProperties {
    private boolean enabled = true;
    private int shards = 64;
    private int maxInFlightPerConnection = 64;
    private RawPayloadMode rawPayloadMode = RawPayloadMode.RETAIN;
    private boolean closeOnNeedLogin = false;

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
}

