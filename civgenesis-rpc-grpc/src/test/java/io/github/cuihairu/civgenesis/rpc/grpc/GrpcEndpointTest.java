package io.github.cuihairu.civgenesis.rpc.grpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcEndpointTest {
    @Test
    void parsesValidUrisAndRejectsInvalidOnes() {
        assertEquals(new GrpcEndpoint("example.com", 8080), GrpcEndpoint.fromUri("grpc://example.com:8080"));
        assertThrows(NullPointerException.class, () -> GrpcEndpoint.fromUri(null));
        assertThrows(IllegalArgumentException.class, () -> GrpcEndpoint.fromUri("http://example.com:8080"));
        assertThrows(IllegalArgumentException.class, () -> GrpcEndpoint.fromUri("grpc://example.com"));
        assertThrows(IllegalArgumentException.class, () -> GrpcEndpoint.fromUri("grpc://:8080"));
        assertThrows(IllegalArgumentException.class, () -> GrpcEndpoint.fromUri("grpc://example.com:-1"));
    }

    @Test
    void configHasInsecureAndTlsModes() {
        assertTrue(GrpcClientConfig.insecure().plaintext());
        assertTrue(!GrpcClientConfig.tls().plaintext());
        assertTrue(new GrpcClientConfig(true).equals(GrpcClientConfig.insecure()));
        assertEquals(8080, new GrpcEndpoint("host", 8080).port());
    }
}
