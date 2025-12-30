package io.github.cuihairu.civgenesis.system.auth;

public final class DenyAllTokenAuthenticator implements TokenAuthenticator {
    @Override
    public AuthResult authenticate(String token) {
        return new AuthResult(0, false);
    }
}

