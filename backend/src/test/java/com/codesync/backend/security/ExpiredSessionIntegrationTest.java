package com.codesync.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real HTTP against the running app: a missing, expired or broken token must get 401 (sign in
 * again), never a 500. A browser that kept yesterday's token used to get a server error.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "codesync.jwt.secret=" + ExpiredSessionIntegrationTest.SECRET
)
class ExpiredSessionIntegrationTest {

    static final String SECRET = "test-only-secret-that-is-long-enough-for-hs256";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtService jwtService;

    private int status(String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/problems")).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        assertEquals(401, status(null));
    }

    @Test
    void anExpiredTokenIsUnauthorizedNotAServerError() throws Exception {
        String expired = Jwts.builder()
                .subject("someone")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertEquals(401, status("Bearer " + expired));
    }

    @Test
    void aBrokenTokenIsUnauthorized() throws Exception {
        assertEquals(401, status("Bearer not.a.token"));
        assertEquals(401, status("Bearer "));
    }

    @Test
    void aValidTokenGetsThrough() throws Exception {
        // The problem list needs a signed-in user but no account row, so any valid token works
        assertEquals(200, status("Bearer " + jwtService.generateToken("nobody-in-particular")));
    }
}
