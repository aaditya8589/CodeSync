package com.codesync.backend.security;

import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads Google's signing keys and keeps them for as long as Google's Cache-Control header
 * allows. Google rotates the keys every few days, so a token signed with a key we have not seen
 * makes us download them again, but at most once a minute: made-up key ids must not turn every
 * request into a call to Google.
 */
@Component
public class GoogleJwksKeys implements GoogleSigningKeys {

    private static final Logger log = LoggerFactory.getLogger(GoogleJwksKeys.class);

    static final URI CERTS_URL = URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(1);
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private record Snapshot(Map<String, Key> keys, Instant expiresAt, Instant fetchedAt) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Instant.EPOCH, Instant.EPOCH);

    @Override
    public Key find(String keyId) {
        Snapshot current = snapshot;
        Instant now = Instant.now();
        boolean expired = now.isAfter(current.expiresAt());
        Key key = current.keys().get(keyId);

        if (key != null && !expired) return key;
        if (!expired && now.isBefore(current.fetchedAt().plus(MIN_REFRESH_INTERVAL))) return null;

        return refresh(current).keys().get(keyId);
    }

    private synchronized Snapshot refresh(Snapshot seen) {
        // Another request refreshed while this one waited for the lock
        if (snapshot != seen) return snapshot;

        Instant now = Instant.now();
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(CERTS_URL).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            Duration maxAge = maxAge(response.headers().firstValue("Cache-Control").orElse(""));
            snapshot = new Snapshot(parse(response.body()), now.plus(maxAge), now);
        } catch (IOException | RuntimeException exception) {
            // Keep the keys we have (they stay valid for days) and try again in a minute
            log.warn("Could not download Google's signing keys: {}", exception.getMessage());
            snapshot = new Snapshot(seen.keys(), now.plus(MIN_REFRESH_INTERVAL), now);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return snapshot;
    }

    static Map<String, Key> parse(String json) {
        JwkSet set = Jwks.setParser().build().parse(json);
        Map<String, Key> keys = new HashMap<>();
        for (Jwk<?> jwk : set.getKeys()) {
            if (jwk.getId() != null && jwk.toKey() instanceof PublicKey key) {
                keys.put(jwk.getId(), key);
            }
        }
        return Map.copyOf(keys);
    }

    static Duration maxAge(String cacheControl) {
        Matcher matcher = MAX_AGE.matcher(cacheControl);
        return matcher.find() ? Duration.ofSeconds(Long.parseLong(matcher.group(1))) : DEFAULT_MAX_AGE;
    }
}
