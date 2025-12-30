package io.github.cuihairu.civgenesis.system.auth;

public interface TokenAuthenticator {
    AuthResult authenticate(String token) throws Exception;
}

