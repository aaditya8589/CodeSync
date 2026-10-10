package com.codesync.backend.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-bytes";

    @Test
    void refusesToStartWithoutASecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 60));
        assertThrows(IllegalStateException.class, () -> new JwtService("   ", 60));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 60));
    }

    @Test
    void refusesASecretTooShortForHs256() {
        assertThrows(IllegalStateException.class, () -> new JwtService("x".repeat(31), 60));
        new JwtService("x".repeat(32), 60);
    }

    @Test
    void refusesANonPositiveLifetime() {
        assertThrows(IllegalStateException.class, () -> new JwtService(SECRET, 0));
    }

    @Test
    void tokenRoundTrips() {
        JwtService service = new JwtService(SECRET, 60);
        assertEquals("alice", service.extractUsername(service.generateToken("alice")));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService ours = new JwtService(SECRET, 60);
        JwtService attacker = new JwtService("a-different-secret-that-is-32-bytes-long", 60);

        String forged = attacker.generateToken("alice");

        assertThrows(JwtException.class, () -> ours.extractUsername(forged));
    }
}
