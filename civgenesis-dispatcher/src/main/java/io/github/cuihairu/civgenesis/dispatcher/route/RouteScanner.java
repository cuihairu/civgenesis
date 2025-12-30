package io.github.cuihairu.civgenesis.dispatcher.route;

import io.github.cuihairu.civgenesis.dispatcher.annotation.GameController;
import io.github.cuihairu.civgenesis.dispatcher.annotation.GameRoute;
import io.github.cuihairu.civgenesis.dispatcher.runtime.RequestContext;
import io.netty.buffer.ByteBuf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class RouteScanner {
    public RouteTable scan(Collection<?> controllers) {
        Objects.requireNonNull(controllers, "controllers");
        Map<Integer, RouteInvoker> routes = new HashMap<>();
        for (Object controller : controllers) {
            if (controller == null) {
                continue;
            }
            Class<?> scanType = resolveScanType(controller.getClass());
            if (!scanType.isAnnotationPresent(GameController.class)) {
                continue;
            }
            for (Method method : scanType.getMethods()) {
                GameRoute route = method.getAnnotation(GameRoute.class);
                if (route == null) {
                    continue;
                }
                if (!Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException("GameRoute method must be public: " + method);
                }
                int msgId = route.id();
                if (routes.containsKey(msgId)) {
                    throw new IllegalArgumentException("Duplicate msgId: " + msgId);
                }
                routes.put(msgId, createInvoker(controller, method, route));
            }
        }
        return new RouteTable(routes);
    }

    private static Class<?> resolveScanType(Class<?> type) {
        Class<?> t = type;
        while (t != null
                && t.getSuperclass() != null
                && t.getSuperclass() != Object.class
                && t.getName().contains("$$")) {
            t = t.getSuperclass();
        }
        return t == null ? type : t;
    }

    private static RouteInvoker createInvoker(Object controller, Method method, GameRoute route) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 || !RequestContext.class.isAssignableFrom(params[0])) {
            throw new IllegalArgumentException("GameRoute method signature must be (RequestContext, Req): " + method);
        }
        Class<?> reqType = params[1];
        boolean raw = ByteBuf.class.isAssignableFrom(reqType);
        boolean returnsVoid = method.getReturnType().equals(void.class);

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method).bindTo(controller);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot access GameRoute method: " + method, e);
        }

        RouteDefinition definition = new RouteDefinition(route.id(), route.open(), route.shardBy());
        if (raw) {
            if (!returnsVoid) {
                throw new IllegalArgumentException("Raw handler must return void: " + method);
            }
            return new RouteInvoker() {
                @Override
                public RouteDefinition definition() {
                    return definition;
                }

                @Override
                public void invoke(RequestContext ctx, io.github.cuihairu.civgenesis.core.protocol.Frame req) throws Throwable {
                    ByteBuf payload = ctx.rawPayload(req);
                    handle.invoke(ctx, payload);
                }
            };
        }

        return new RouteInvoker() {
            @Override
            public RouteDefinition definition() {
                return definition;
            }

            @Override
            public void invoke(RequestContext ctx, io.github.cuihairu.civgenesis.core.protocol.Frame req) throws Throwable {
                Object decoded = ctx.decode(reqType, req);
                if (returnsVoid) {
                    handle.invoke(ctx, decoded);
                    return;
                }
                Object resp = handle.invoke(ctx, decoded);
                ctx.reply(resp);
            }
        };
    }
}
