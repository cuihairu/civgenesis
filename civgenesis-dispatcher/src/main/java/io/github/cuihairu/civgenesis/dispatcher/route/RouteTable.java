package io.github.cuihairu.civgenesis.dispatcher.route;

import java.util.Map;
import java.util.Objects;

public final class RouteTable {
    private final Map<Integer, RouteInvoker> routes;

    public RouteTable(Map<Integer, RouteInvoker> routes) {
        this.routes = Map.copyOf(Objects.requireNonNull(routes, "routes"));
    }

    public RouteInvoker get(int msgId) {
        return routes.get(msgId);
    }

    public Map<Integer, RouteInvoker> asMap() {
        return routes;
    }
}

