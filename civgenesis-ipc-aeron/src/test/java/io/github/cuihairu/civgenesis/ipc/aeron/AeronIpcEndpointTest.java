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

    @Test
    void rejectsNonAeronSchemeAndTransport() {
        assertThrows(IllegalArgumentException.class,
                () -> AeronIpcEndpoint.parse(new Endpoint("udp:ipc?dir=/tmp&streamId=1"), "/tmp"));
        assertThrows(IllegalArgumentException.class,
                () -> AeronIpcEndpoint.parse(new Endpoint("aeron:udp?dir=/tmp&streamId=1"), "/tmp"));
    }

    @Test
    void rejectsMissingStreamIdWithDefaultDir() {
        // no query at all: the default dir is used but the stream id is still required
        assertThrows(IllegalArgumentException.class,
                () -> AeronIpcEndpoint.parse(new Endpoint("aeron:ipc"), "/tmp"));
    }

    @Test
    void rejectsInvalidStreamIdValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AeronIpcEndpoint.parse(new Endpoint("aeron:ipc?dir=/tmp&streamId=abc"), null));
        assertTrue(ex.getMessage().contains("streamId"));
    }

    @Test
    void toleratesBlankSegmentsAndValuelessParameters() {
        AeronIpcEndpoint ep = AeronIpcEndpoint.parse(
                new Endpoint("aeron:ipc?dir=/tmp&&in=5&out=6"), null);
        assertEquals(5, ep.inStreamId());
        assertEquals(6, ep.outStreamId());

        // a valueless parameter is decoded as an empty string and ignored
        AeronIpcEndpoint valueless = AeronIpcEndpoint.parse(
                new Endpoint("aeron:ipc?dir=/tmp&in=7&out=8&flag"), null);
        assertEquals(7, valueless.inStreamId());
        assertEquals(8, valueless.outStreamId());
    }
}

