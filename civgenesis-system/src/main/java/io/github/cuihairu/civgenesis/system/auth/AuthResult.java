package io.github.cuihairu.civgenesis.system.auth;

public record AuthResult(
        long playerId,
        boolean kickExistingSession
) {
    public static AuthResult of(long playerId) {
        return new AuthResult(playerId, true);
    }
}

