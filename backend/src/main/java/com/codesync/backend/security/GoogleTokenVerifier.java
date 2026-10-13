package com.codesync.backend.security;

import com.codesync.backend.exception.GoogleSignInDisabledException;
import com.codesync.backend.exception.InvalidGoogleTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Set;

/**
 * Checks a Google ID token the way Google's documentation asks: signed with one of Google's
 * current keys (RS256), issued by Google, issued for this app's client id, and not expired.
 * Only then is the email in it trusted.
 */
@Component
public class GoogleTokenVerifier {

    static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final String clientId;
    private final GoogleSigningKeys keys;

    public GoogleTokenVerifier(@Value("${codesync.google.client-id:}") String clientId, GoogleSigningKeys keys) {
        this.clientId = clientId == null ? "" : clientId.strip();
        this.keys = keys;
    }

    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    /** Public: the browser needs it to show Google's button. */
    public String clientId() {
        return isEnabled() ? clientId : null;
    }

    public GoogleAccount verify(String idToken) {
        if (!isEnabled()) {
            throw new GoogleSignInDisabledException("Google sign-in is not enabled on this server");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidGoogleTokenException("Missing Google credential");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            // Only Google's algorithm; anything else (HS256 signed with a public
                            // key, "none") is refused before a key is even chosen
                            if (!"RS256".equals(header.getAlgorithm()) || header.getKeyId() == null) {
                                throw new InvalidGoogleTokenException("Not a Google ID token");
                            }
                            Key key = keys.find(header.getKeyId());
                            if (key == null) {
                                throw new InvalidGoogleTokenException("Unknown Google signing key");
                            }
                            return key;
                        }
                    })
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidGoogleTokenException("Google sign-in could not be verified");
        }

        if (!ISSUERS.contains(claims.getIssuer())) {
            throw new InvalidGoogleTokenException("Not issued by Google");
        }
        // A token Google issued to another website must not sign anyone in here
        if (claims.getAudience() == null || !claims.getAudience().contains(clientId)) {
            throw new InvalidGoogleTokenException("Issued for a different application");
        }
        // jjwt only checks "exp" when it is present
        if (claims.getExpiration() == null) {
            throw new InvalidGoogleTokenException("Google credential has no expiry");
        }

        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new InvalidGoogleTokenException("Google did not share an email address");
        }

        Object verified = claims.get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new GoogleAccount(subject, email.strip(), emailVerified, claims.get("name", String.class));
    }
}
