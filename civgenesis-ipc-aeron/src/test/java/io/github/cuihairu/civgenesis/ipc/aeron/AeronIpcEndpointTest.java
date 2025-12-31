package io.github.cuihairu.civgenesis.ipc.aeron;

import io.github.cuihairu.civgenesis.registry.Endpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AeronIpcEndpointTest {
    @Test
    void parsesDirAndSingleStreamId() {
        AeronIpcEndpoint ep = AeronIpcEndpoint.parse(new Endpoint("aeron:ipc?dir=/dev/shm/aeron&streamId=1101"), null);
        assertEquals("/dev/shm/aeron", ep.dir());
        assertEquals(1101, ep.inStreamId());
        assertEquals(1101, ep.outStreamId());
    }

    @Test
    void parsesDirAndInOutStreams() {
        AeronIpcEndpoint ep = AeronIpcEndpoint.parse(new Endpoint("aeron:ipc?dir=/dev/shm/aeron&inStreamId=1101&outStreamId=1102"), null);
        assertEquals("/dev/shm/aeron", ep.dir());
        assertEquals(1101, ep.inStreamId());
        assertEquals(1102, ep.outStreamId());
    }

    @Test
    void usesDefaultDirWhenMissing() {
        AeronIpcEndpoint ep = AeronIpcEndpoint.parse(new Endpoint("aeron:ipc?streamId=1101"), "/tmp/aeron");
        assertEquals("/tmp/aeron", ep.dir());
        assertEquals(1101, ep.inStreamId());
        assertEquals(1101, ep.outStreamId());
    }

    @Test
    void rejectsWhenDirMissingAndNoDefault() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AeronIpcEndpoint.parse(new Endpoint("aeron:ipc?streamId=1101"), null));
        assertTrue(ex.getMessage().contains("missing aeron dir"));
    }
}

