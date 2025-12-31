package io.github.cuihairu.civgenesis.examples.echo.spi;

import io.github.cuihairu.civgenesis.system.auth.AuthResult;
import io.github.cuihairu.civgenesis.system.auth.TokenAuthenticator;

public final class DemoTokenAuthenticator implements TokenAuthenticator {
    @Override
    public AuthResult authenticate(String token) {
        String t = token == null ? "" : token.trim();
        if (t.startsWith("p:")) {
            long id = Long.parseLong(t.substring(2));
            return new AuthResult(id, true);
        }
        long id = Long.parseLong(t);
        return new AuthResult(id, true);
    }
}

