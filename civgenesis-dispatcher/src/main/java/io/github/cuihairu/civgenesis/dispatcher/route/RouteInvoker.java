package io.github.cuihairu.civgenesis.dispatcher.route;

import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.github.cuihairu.civgenesis.core.protocol.Frame;

public interface RouteInvoker {
    RouteDefinition definition();

    void invoke(RequestContext ctx, Frame req) throws Throwable;
}

