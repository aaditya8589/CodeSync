package com.codesync.backend.security;

import com.codesync.backend.exception.GoogleSignInDisabledException;
import com.codesync.backend.exception.InvalidGoogleTokenException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tokens are signed here with a local key that stands in for Google's. */
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "1234-test.apps.googleusercontent.com";
    private static final KeyPair GOOGLE = rsa();
    private static final KeyPair OTHER = rsa();

    private final GoogleTokenVerifier verifier =
            new GoogleTokenVerifier(CLIENT_ID, Map.of("key-1", GOOGLE.getPublic())::get);

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** A token as Google issues it; each test changes one thing. */
    private static String token(UnaryOperator<JwtBuilder> change) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .header().keyId("key-1").and()
                .issuer("https://accounts.google.com")
                .audience().add(CLIENT_ID).and()
                .subject("109876543210")
                .claim("email", "Ada.Lovelace@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Ada Lovelace")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(GOOGLE.getPrivate());
        return change.apply(builder).compact();
    }

    private void rejected(String token) {
        assertThrows(InvalidGoogleTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void acceptsAGenuineToken() {
        GoogleAccount account = verifier.verify(token(b -> b));
        assertEquals("109876543210", account.subject());
        assertEquals("Ada.Lovelace@gmail.com", account.email());
        assertTrue(account.emailVerified());
        assertEquals("Ada Lovelace", account.name());
    }

    @Test
    void acceptsTheIssuerWithoutHttpsAndAStringEmailVerified() {
        GoogleAccount account = verifier.verify(token(b -> b.issuer("accounts.google.com").claim("email_verified", "true")));
        assertTrue(account.emailVerified());
    }

    @Test
    void reportsAnUnverifiedEmail() {
        assertFalse(verifier.verify(token(b -> b.claim("email_verified", false))).emailVerified());
    }

    @Test
    void rejectsATokenForAnotherApp() {
        rejected(token(b -> b.audience().single("someone-else.apps.googleusercontent.com")));
    }

    @Test
    void rejectsAnotherIssuer() {
        rejected(token(b -> b.issuer("https://evil.example.com")));
    }

    @Test
    void rejectsAnExpiredToken() {
        rejected(token(b -> b.expiration(Date.from(Instant.now().minusSeconds(120)))));
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        rejected(token(b -> b.expiration(null)));
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        rejected(token(b -> b.signWith(OTHER.getPrivate())));
    }

    @Test
    void rejectsAnUnknownKeyId() {
        rejected(token(b -> b.header().keyId("key-2").and()));
    }

    @Test
    void rejectsHs256SignedWithGooglesPublicKey() {
        // The classic algorithm-confusion attack: the public key is public, so if it were accepted
        // as an HMAC secret, anyone could sign tokens
        byte[] publicKeyBytes = GOOGLE.getPublic().getEncoded();
        rejected(token(b -> b.signWith(new SecretKeySpec(publicKeyBytes, "HmacSHA256"), Jwts.SIG.HS256)));
    }

    @Test
    void rejectsAnUnsignedToken() {
        String unsigned = Jwts.builder().header().keyId("key-1").and()
                .issuer("https://accounts.google.com").audience().add(CLIENT_ID).and()
                .subject("1").claim("email", "a@b.c").expiration(Date.from(Instant.now().plusSeconds(60))).compact();
        rejected(unsigned);
    }

    @Test
    void rejectsATamperedPayload() {
        String[] parts = token(b -> b).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
                .replace("Ada.Lovelace@gmail.com", "attacker@gmail.com");
        rejected(parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + parts[2]);
    }

    @Test
    void rejectsATokenWithoutEmail() {
        rejected(token(b -> b.claim("email", null)));
    }

    @Test
    void rejectsGarbageAndBlank() {
        rejected("not-a-token");
        rejected("");
        rejected(null);
    }

    @Test
    void isOffWithoutAClientId() {
        GoogleTokenVerifier off = new GoogleTokenVerifier("  ", id -> null);
        assertFalse(off.isEnabled());
        assertNull(off.clientId());
        assertThrows(GoogleSignInDisabledException.class, () -> off.verify(token(b -> b)));
    }

    @Test
    void readsGooglesKeyFormat() {
        RSAPublicKey key = (RSAPublicKey) GOOGLE.getPublic();
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        // Same shape as https://www.googleapis.com/oauth2/v3/certs
        String json = """
                {"keys": [
                  {"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "key-1", "n": "%s", "e": "%s"}
                ]}""".formatted(base64.encodeToString(unsigned(key.getModulus().toByteArray())),
                base64.encodeToString(unsigned(key.getPublicExponent().toByteArray())));

        GoogleTokenVerifier fromJson = new GoogleTokenVerifier(CLIENT_ID, GoogleJwksKeys.parse(json)::get);
        assertEquals("109876543210", fromJson.verify(token(b -> b)).subject());
    }

    @Test
    void cachesKeysForAsLongAsGoogleSays() {
        assertEquals(19_845, GoogleJwksKeys.maxAge("public, max-age=19845, must-revalidate, no-transform").getSeconds());
        assertEquals(3600, GoogleJwksKeys.maxAge("no-cache").getSeconds());
    }

    // BigInteger adds a leading zero byte when the top bit is set; JWK numbers have none
    private static byte[] unsigned(byte[] bytes) {
        return bytes.length > 1 && bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
}
