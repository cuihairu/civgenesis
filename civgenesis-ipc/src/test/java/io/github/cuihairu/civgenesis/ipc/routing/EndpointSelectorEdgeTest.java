package io.github.cuihairu.civgenesis.ipc.routing;

import io.github.cuihairu.civgenesis.registry.Endpoint;
import io.github.cuihairu.civgenesis.registry.InstanceId;
import io.github.cuihairu.civgenesis.registry.ServiceInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointSelectorEdgeTest {
    @Test
    void classifiesAndValidatesEndpointKinds() {
        assertEquals(EndpointKind.TCP, EndpointSelector.kindOf(new Endpoint("TCP://localhost:1")));
        assertEquals(EndpointKind.UDS, EndpointSelector.kindOf(new Endpoint("uds:/tmp/x")));
        assertEquals(EndpointKind.GRPC, EndpointSelector.kindOf(new Endpoint("grpc://host:1")));
        assertEquals(EndpointKind.GRPC, EndpointSelector.kindOf(new Endpoint("grpcs://host:1")));
        assertEquals(EndpointKind.AERON_IPC, EndpointSelector.kindOf(new Endpoint("aeron:ipc?streamId=1")));
        assertEquals(EndpointKind.AERON_IPC, EndpointSelector.kindOf(new Endpoint("aeron:ipc:foo?streamId=1")));
        assertEquals(EndpointKind.UNKNOWN, EndpointSelector.kindOf(new Endpoint("not-a-uri")));
        assertEquals(EndpointKind.UNKNOWN, EndpointSelector.kindOf(new Endpoint("mail://x")));
    }

    @Test
    void choosesSameHostIpcAndFallsBackCorrectly() {
        long self = InstanceId.encode(1, 2, 3, 4, 5);
        ServiceInstance aeronFirst = instance(self, List.of(new Endpoint("aeron:ipc?streamId=1"), new Endpoint("uds:/tmp/a"), new Endpoint("tcp://127.0.0.1:9")));
        assertEquals(EndpointKind.AERON_IPC, EndpointSelector.selectBest(self, aeronFirst).orElseThrow().kind());

        ServiceInstance uds = instance(self, List.of(new Endpoint("uds:/tmp/a")));
        assertEquals("uds:/tmp/a", EndpointSelector.selectBest(self, uds).orElseThrow().endpoint().uri());

        ServiceInstance tcpLoop = instance(self, List.of(new Endpoint("tcp://localhost:9")));
        assertEquals("tcp://localhost:9", EndpointSelector.selectBest(self, tcpLoop).orElseThrow().endpoint().uri());

        ServiceInstance remoteTcp = instance(0, List.of(new Endpoint("tcp://10.0.0.1:9")));
        assertEquals("tcp://10.0.0.1:9", EndpointSelector.selectBest(self, remoteTcp).orElseThrow().endpoint().uri());
    }

    @Test
    void coversRemainingFallbackAndClassificationBranches() {
        long same = InstanceId.encode(1, 2, 3, 4, 5);

        // null endpoints are treated as an empty list
        ServiceInstance nullEndpoints = new ServiceInstance("svc", "i", same, "10.0.0.1", 7077, 0, null, Map.of());
        assertEquals("tcp://127.0.0.1:7077", EndpointSelector.selectBest(same, nullEndpoints).orElseThrow().endpoint().uri());

        // same-host with only a non-loopback tcp endpoint falls back to loopback
        ServiceInstance nonLoopback = instance(same, List.of(new Endpoint("tcp://10.0.0.1:9")));
        assertEquals("tcp://127.0.0.1:7077", EndpointSelector.selectBest(same, nonLoopback).orElseThrow().endpoint().uri());

        // a blank host on a remote instance falls back to loopback when the port is present
        ServiceInstance blankHost = new ServiceInstance("svc", "i", 0, "", 8080, 0, List.of(), Map.of());
        assertEquals("tcp://127.0.0.1:8080", EndpointSelector.selectBest(0, blankHost).orElseThrow().endpoint().uri());
        ServiceInstance nullHost = new ServiceInstance("svc", "i", 0, null, 8080, 0, List.of(), Map.of());
        assertEquals("tcp://127.0.0.1:8080", EndpointSelector.selectBest(0, nullHost).orElseThrow().endpoint().uri());

        // aeron with a non-ipc scheme part is UNKNOWN
        assertEquals(EndpointKind.UNKNOWN, EndpointSelector.kindOf(new Endpoint("aeron:udp?streamId=1")));
        // a malformed URI is caught and classified as UNKNOWN
        assertEquals(EndpointKind.UNKNOWN, EndpointSelector.kindOf(new Endpoint("http://exa mple")));
        // a host-less tcp URI reaches the null-host check in isLoopbackTcp
        ServiceInstance hostlessTcp = instance(0, List.of(new Endpoint("tcp:///x:9")));
        assertEquals("tcp:///x:9", EndpointSelector.selectBest(0, hostlessTcp).orElseThrow().endpoint().uri());
    }

    @Test
    void usesPortFallbackAndEmptyCases() {
        long same = InstanceId.encode(1, 2, 3, 4, 5);
        ServiceInstance loopbackPort = instance(same, List.of());
        assertEquals("tcp://127.0.0.1:7077", EndpointSelector.selectBest(same, loopbackPort).orElseThrow().endpoint().uri());

        ServiceInstance publicPort = instance(0, List.of());
        assertEquals("tcp://10.1.2.3:7077", EndpointSelector.selectBest(same, publicPort).orElseThrow().endpoint().uri());

        ServiceInstance noPort = new ServiceInstance("svc", "i", 1, "10.0.0.1", 0, 0, List.of(), Map.of());
        assertEquals(Optional.empty(), EndpointSelector.selectBest(same, noPort));

        ServiceInstance mismatchedUdsOnly = instance(0, List.of(new Endpoint("uds:/x")));
        assertEquals("tcp://10.1.2.3:7077", EndpointSelector.selectBest(0, mismatchedUdsOnly).orElseThrow().endpoint().uri());

        assertThrows(NullPointerException.class, () -> EndpointSelector.selectBest(1, null));
        SelectedEndpoint selected = EndpointSelector.selectBest(same, loopbackPort).orElseThrow();
        assertEquals(EndpointKind.TCP, selected.kind());
        assertEquals("tcp://127.0.0.1:7077", selected.endpoint().uri());
    }

    private static ServiceInstance instance(long instanceId, List<Endpoint> endpoints) {
        return new ServiceInstance("svc", "i", instanceId, "10.1.2.3", 7077, 0, endpoints, Map.of());
    }
}
