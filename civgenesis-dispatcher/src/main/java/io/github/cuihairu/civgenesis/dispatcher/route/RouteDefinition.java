package io.github.cuihairu.civgenesis.dispatcher.route;

import java.util.Objects;

public final class RouteDefinition {
    private final int msgId;
    private final boolean open;
    private final ShardBy shardBy;

    public RouteDefinition(int msgId, boolean open, ShardBy shardBy) {
        this.msgId = msgId;
        this.open = open;
        this.shardBy = Objects.requireNonNull(shardBy, "shardBy");
    }

    public int msgId() {
        return msgId;
    }

    public boolean open() {
        return open;
    }

    public ShardBy shardBy() {
        return shardBy;
    }
}

