package io.github.cuihairu.civgenesis.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryValuesTest {
    @Test
    void instanceIdEncodesDecodesAndValidatesRanges() {
        long id = InstanceId.encode(3, 7, 9, 11, 13);
        assertEquals(3, InstanceId.region(id));
        assertEquals(7, InstanceId.host(id));
        assertEquals(9, InstanceId.processType(id));
        assertEquals(11, InstanceId.index(id));
        assertEquals(13, InstanceId.reserved(id));
        assertThrows(IllegalArgumentException.class, () -> InstanceId.encode(4096, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> InstanceId.encode(0, 1_048_576, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> InstanceId.encode(0, 0, 256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> InstanceId.encode(0, 0, 0, 4096, 0));
        assertThrows(IllegalArgumentException.class, () -> InstanceId.encode(0, 0, 0, 0, 4096));
    }

    @Test
    void metadataRoundTripsUnsignedIdsEndpointsAndBlanks() {
        List<Endpoint> endpoints = List.of(new Endpoint("tcp://127.0.0.1:9000"), new Endpoint("aeron:ipc?streamId=1"));
        Map<String, String> metadata = RegistryMetadata.encode(-1, -2, endpoints);
        assertEquals(-1L, RegistryMetadata.decodeInstanceId(metadata));
        assertEquals(-2L, RegistryMetadata.decodeTransportCaps(metadata));
        assertEquals(endpoints, RegistryMetadata.decodeEndpoints(metadata));
        assertTrue(RegistryMetadata.decodeEndpoints(Map.of()).isEmpty());
        assertTrue(RegistryMetadata.decodeEndpoints(Map.of(RegistryMetadata.CG_ENDPOINTS, ",,")).isEmpty());
        assertEquals(0L, RegistryMetadata.decodeInstanceId(Map.of()));
        assertEquals(0L, RegistryMetadata.decodeTransportCaps(Map.of()));
    }

    @Test
    void metadataHandlesEmptyEndpointListsAndBlankSegments() {
        // null and empty endpoint lists encode to an empty string
        assertEquals("", RegistryMetadata.encode(1, 2, null).get(RegistryMetadata.CG_ENDPOINTS));
        assertEquals("", RegistryMetadata.encode(1, 2, List.of()).get(RegistryMetadata.CG_ENDPOINTS));
        // blank segments inside a non-blank value are skipped while real ones decode
        List<Endpoint> endpoints = RegistryMetadata.decodeEndpoints(
                Map.of(RegistryMetadata.CG_ENDPOINTS, "tcp%3A%2F%2Fa%3A1,,tcp://b:2,"));
        assertEquals(List.of(new Endpoint("tcp://a:1"), new Endpoint("tcp://b:2")), endpoints);
    }

    @Test
    void serviceRegistryDefaultCloseIsNoop() throws Exception {
        ServiceRegistry registry = new ServiceRegistry() {
            @Override
            public void register(ServiceRegistration registration) {
            }

            @Override
            public void deregister(ServiceRegistration registration) {
            }
        };
        registry.close();
    }

    @Test
    void endpointAndRecordsValidateValues() {
        assertThrows(NullPointerException.class, () -> new Endpoint(null));
        assertThrows(IllegalArgumentException.class, () -> new Endpoint(" "));
        assertEquals(new Endpoint("tcp://a:1"), new Endpoint("tcp://a:1"));
        assertEquals("tcp://a:1", new Endpoint("tcp://a:1").uri());

        ServiceRegistration registration = new ServiceRegistration("svc", "group", "127.0.0.1", 1, "i", 2, 3, List.of(), Map.of());
        ServiceInstance instance = new ServiceInstance("svc", "i", 2, "127.0.0.1", 1, 3, List.of(), Map.of());
        assertEquals("svc", registration.serviceName());
        assertEquals("svc", instance.serviceName());
        assertEquals(31L, TransportCaps.TCP | TransportCaps.UDS | TransportCaps.SHM_AERON_IPC
                | TransportCaps.SHM_MMAP_QUEUE | TransportCaps.GRPC);
    }
}
