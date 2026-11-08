package io.github.cuihairu.civgenesis.examples.echo.spi;

import io.github.cuihairu.civgenesis.system.auth.AuthResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoProvidersTest {
    @Test
    void authenticatorAcceptsPrefixedAndNumericTokens() {
        assertEquals(12, new DemoTokenAuthenticator().authenticate("p:12").playerId());
        assertTrue(new DemoTokenAuthenticator().authenticate("34").kickExistingSession());
        assertThrows(NumberFormatException.class, () -> new DemoTokenAuthenticator().authenticate("bad"));
        assertThrows(NumberFormatException.class, () -> new DemoTokenAuthenticator().authenticate(null));
        assertEquals(7, AuthResult.of(7).playerId());
    }

    @Test
    void snapshotProviderBuildsReadableState() {
        var snapshot = new DemoSnapshotProvider().snapshot(9, 42);
        assertEquals("snapshot(playerId=9,rev=42)", new String(snapshot.snapshotBytes(), StandardCharsets.UTF_8));
        assertTrue(snapshot.serverStateRevision() > 0);
    }
}
