package io.github.cuihairu.civgenesis.jobs.lease.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLeaseProviderTest {
    private static RedisLeaseProvider create(RedisClient client, String prefix) {
        return new RedisLeaseProvider(client, prefix);
    }

    @Test
    void acquiresFailsAndSafelyReleasesLease() {
        RedisClient client = mock(RedisClient.class);
        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> connection = mock(StatefulRedisConnection.class);
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(client.connect()).thenReturn(connection);
        when(connection.sync()).thenReturn(commands);
        when(commands.incr("prefix:name:token")).thenReturn(7L, 8L);
        when(commands.set(eq("prefix:name:lock"), eq("7"), any())).thenReturn("OK");
        when(commands.set(eq("prefix:name:lock"), eq("8"), any())).thenReturn(null);
        when(commands.eval(anyString(), any(), any(String[].class), eq("prefix:name:lock"), eq("7"))).thenReturn(1L);

        RedisLeaseProvider provider = new RedisLeaseProvider(client, "prefix:");
        Optional<io.github.cuihairu.civgenesis.jobs.Lease> lease = provider.tryAcquire("name", Duration.ZERO);
        assertTrue(lease.isPresent());
        assertEquals(7, lease.orElseThrow().fencingToken());
        lease.orElseThrow().close();
        lease.orElseThrow().close();
        assertEquals(Optional.empty(), provider.tryAcquire("name", Duration.ofMillis(1)));
        provider.close();
        verify(connection).close();
        verify(client).shutdown();
        assertThrows(NullPointerException.class, () -> create(null, "prefix:"));
        assertThrows(NullPointerException.class, () -> new RedisLeaseProvider(mock(RedisClient.class), null));
        assertThrows(NullPointerException.class, () -> provider.tryAcquire(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> provider.tryAcquire("name", null));
    }
}
