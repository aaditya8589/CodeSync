package com.codesync.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    // HS256 needs a key of at least 256 bits
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final Duration tokenLifetime;

    public JwtService(
            @Value("${codesync.jwt.secret:}") String secret,
            @Value("${codesync.jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        // Fail at startup rather than run with a guessable key: anyone who knows the key
        // can sign a token for any username.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Set it to a random string of at least "
                            + MIN_SECRET_BYTES + " characters.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short: " + bytes.length + " bytes, at least "
                            + MIN_SECRET_BYTES + " are needed.");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("codesync.jwt.expiration-minutes must be positive");
        }

        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.tokenLifetime = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(String username) {
        Date now = new Date();

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + tokenLifetime.toMillis()))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
