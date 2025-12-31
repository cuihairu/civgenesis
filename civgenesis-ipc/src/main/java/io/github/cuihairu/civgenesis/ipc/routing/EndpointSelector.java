package io.github.cuihairu.civgenesis.ipc.routing;

import io.github.cuihairu.civgenesis.registry.Endpoint;
import io.github.cuihairu.civgenesis.registry.InstanceId;
import io.github.cuihairu.civgenesis.registry.ServiceInstance;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class EndpointSelector {
    private EndpointSelector() {}

    public static Optional<SelectedEndpoint> selectBest(long selfInstanceId, ServiceInstance target) {
        Objects.requireNonNull(target, "target");
        boolean sameHost = selfInstanceId > 0 && target.instanceIdLong() > 0 && InstanceId.sameHost(selfInstanceId, target.instanceIdLong());

        List<Endpoint> endpoints = target.endpoints() == null ? List.of() : target.endpoints();
        if (endpoints.isEmpty()) {
            return fallbackTcp(target, sameHost);
        }

        if (sameHost) {
            Optional<Endpoint> aeron = endpoints.stream().filter(e -> kindOf(e) == EndpointKind.AERON_IPC).findFirst();
            if (aeron.isPresent()) {
                return Optional.of(new SelectedEndpoint(EndpointKind.AERON_IPC, aeron.get()));
            }
            Optional<Endpoint> uds = endpoints.stream().filter(e -> kindOf(e) == EndpointKind.UDS).findFirst();
            if (uds.isPresent()) {
                return Optional.of(new SelectedEndpoint(EndpointKind.UDS, uds.get()));
            }
            Optional<Endpoint> tcpLoop = endpoints.stream()
                    .filter(e -> kindOf(e) == EndpointKind.TCP)
                    .filter(EndpointSelector::isLoopbackTcp)
                    .findFirst();
            if (tcpLoop.isPresent()) {
                return Optional.of(new SelectedEndpoint(EndpointKind.TCP, tcpLoop.get()));
            }
            return fallbackTcp(target, true);
        }

        Optional<Endpoint> tcp = endpoints.stream()
                .filter(e -> kindOf(e) == EndpointKind.TCP)
                .filter(e -> !isLoopbackTcp(e))
                .findFirst();
        if (tcp.isPresent()) {
            return Optional.of(new SelectedEndpoint(EndpointKind.TCP, tcp.get()));
        }
        return fallbackTcp(target, false);
    }

    public static EndpointKind kindOf(Endpoint endpoint) {
        try {
            URI uri = URI.create(endpoint.uri());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return EndpointKind.UNKNOWN;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            return switch (scheme) {
                case "tcp" -> EndpointKind.TCP;
                case "uds" -> EndpointKind.UDS;
                case "grpc", "grpcs" -> EndpointKind.GRPC;
                case "aeron" -> {
                    String ssp = uri.getSchemeSpecificPart();
                    if (ssp != null && ssp.startsWith("ipc")) {
                        yield EndpointKind.AERON_IPC;
                    }
                    yield EndpointKind.UNKNOWN;
                }
                default -> EndpointKind.UNKNOWN;
            };
        } catch (RuntimeException e) {
            return EndpointKind.UNKNOWN;
        }
    }

    private static Optional<SelectedEndpoint> fallbackTcp(ServiceInstance target, boolean loopback) {
        if (target.port() <= 0) {
            return Optional.empty();
        }
        String host = loopback ? "127.0.0.1" : target.ip();
        if (host == null || host.isBlank()) {
            host = loopback ? "127.0.0.1" : "127.0.0.1";
        }
        return Optional.of(new SelectedEndpoint(EndpointKind.TCP, new Endpoint("tcp://" + host + ":" + target.port())));
    }

    private static boolean isLoopbackTcp(Endpoint e) {
        try {
            URI uri = URI.create(e.uri());
            if (!"tcp".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1");
        } catch (RuntimeException ex) {
            return false;
        }
    }
}

