package io.github.cuihairu.civgenesis.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InstanceIdTest {
    @Test
    void encodeDecodeRoundTrip() {
        long id = InstanceId.encode(12, 345_678, 7, 123, 0);
        assertEquals(12, InstanceId.region(id));
        assertEquals(345_678, InstanceId.host(id));
        assertEquals(7, InstanceId.processType(id));
        assertEquals(123, InstanceId.index(id));
        assertEquals(0, InstanceId.reserved(id));
    }

    @Test
    void sameHostUsesRegionAndHost() {
        long a = InstanceId.encode(1, 2, 3, 1, 0);
        long b = InstanceId.encode(1, 2, 9, 2, 0);
        long c = InstanceId.encode(1, 3, 3, 1, 0);
        long d = InstanceId.encode(2, 2, 3, 1, 0);
        assertTrue(InstanceId.sameHost(a, b));
        assertFalse(InstanceId.sameHost(a, c));
        assertFalse(InstanceId.sameHost(a, d));
    }
}

