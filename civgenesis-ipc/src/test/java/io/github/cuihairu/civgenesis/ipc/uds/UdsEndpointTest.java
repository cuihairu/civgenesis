package io.github.cuihairu.civgenesis.ipc.uds;

import io.github.cuihairu.civgenesis.registry.Endpoint;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdsEndpointTest {
    @Test
    void parsesAndValidatesUdsEndpoint() {
        assertEquals(Path.of("/tmp/civgenesis.ipc"), UdsEndpoint.toPath(new Endpoint("uds:/tmp/civgenesis.ipc")));
        assertThrows(NullPointerException.class, () -> UdsEndpoint.toPath(null));
        assertThrows(IllegalArgumentException.class, () -> UdsEndpoint.toPath(new Endpoint("tcp://localhost:1")));
        assertThrows(IllegalArgumentException.class, () -> UdsEndpoint.toPath(new Endpoint("uds:opaque")));
    }

    @Test
    void validatesOptionsAndDefaults() {
        assertEquals(1024 * 1024, UdsIpcOptions.defaults().maxFrameBytes());
        assertEquals(4 * 1024 * 1024, UdsIpcOptions.defaults().maxQueueBytes());
        assertTrue(new UdsIpcOptions(1, 1).equals(new UdsIpcOptions(1, 1)));
    }
}
