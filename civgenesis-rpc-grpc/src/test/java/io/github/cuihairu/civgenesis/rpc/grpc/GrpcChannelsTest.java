package io.github.cuihairu.civgenesis.rpc.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcChannelsTest {

    @Test
    void buildsPlaintextAndTlsChannelsAndRejectsNulls() {
        try (MockedStatic<ManagedChannelBuilder> builders = Mockito.mockStatic(ManagedChannelBuilder.class)) {
            ManagedChannel plain = mock(ManagedChannel.class);
            @SuppressWarnings("rawtypes")
            ManagedChannelBuilder plainBuilder = mock(ManagedChannelBuilder.class);
            when(ManagedChannelBuilder.forAddress(eq("example.com"), eq(8080))).thenReturn(plainBuilder);
            when(plainBuilder.usePlaintext()).thenReturn(plainBuilder);
            when(plainBuilder.build()).thenReturn(plain);

            assertSame(plain, GrpcChannels.forEndpoint(new GrpcEndpoint("example.com", 8080), GrpcClientConfig.insecure()));
            verify(plainBuilder).usePlaintext();

            ManagedChannel tls = mock(ManagedChannel.class);
            @SuppressWarnings("rawtypes")
            ManagedChannelBuilder tlsBuilder = mock(ManagedChannelBuilder.class);
            when(ManagedChannelBuilder.forAddress(eq("host"), eq(443))).thenReturn(tlsBuilder);
            when(tlsBuilder.build()).thenReturn(tls);
            assertSame(tls, GrpcChannels.forEndpoint(new GrpcEndpoint("host", 443), GrpcClientConfig.tls()));

            assertSame(plain, GrpcChannels.forUri("grpc://example.com:8080", GrpcClientConfig.insecure()));
        }
        assertThrows(NullPointerException.class,
                () -> GrpcChannels.forEndpoint(null, GrpcClientConfig.insecure()));
        assertThrows(NullPointerException.class,
                () -> GrpcChannels.forEndpoint(new GrpcEndpoint("h", 1), null));
    }
}
