package io.github.cuihairu.civgenesis.ipc.routing;

import io.github.cuihairu.civgenesis.registry.Endpoint;
import io.github.cuihairu.civgenesis.registry.InstanceId;
import io.github.cuihairu.civgenesis.registry.ServiceInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointSelectorTest {
    @Test
    void sameHostPrefersAeronThenUdsThenLoopbackTcp() {
        long self = InstanceId.encode(1, 2, 1, 1, 0);
        long targetId = InstanceId.encode(1, 2, 9, 2, 0);
        ServiceInstance target = new ServiceInstance(
                "svc",
                "i",
                targetId,
                "10.0.0.1",
                9001,
                0,
                List.of(
                        new Endpoint("tcp://10.0.0.1:9001"),
                        new Endpoint("uds:///tmp/svc.sock"),
                        new Endpoint("aeron:ipc?dir=/dev/shm/aeron&streamId=1101"),
                        new Endpoint("tcp://127.0.0.1:9001")
                ),
                Map.of()
        );

        SelectedEndpoint sel = EndpointSelector.selectBest(self, target).orElseThrow();
        assertEquals(EndpointKind.AERON_IPC, sel.kind());
    }

    @Test
    void remotePrefersNonLoopbackTcp() {
        long self = InstanceId.encode(1, 2, 1, 1, 0);
        long targetId = InstanceId.encode(1, 3, 9, 2, 0);
        ServiceInstance target = new ServiceInstance(
                "svc",
                "i",
                targetId,
                "10.0.0.1",
                9001,
                0,
                List.of(
                        new Endpoint("tcp://127.0.0.1:9001"),
                        new Endpoint("tcp://10.0.0.1:9001")
                ),
                Map.of()
        );

        SelectedEndpoint sel = EndpointSelector.selectBest(self, target).orElseThrow();
        assertEquals(EndpointKind.TCP, sel.kind());
        assertEquals("tcp://10.0.0.1:9001", sel.endpoint().uri());
    }
}

