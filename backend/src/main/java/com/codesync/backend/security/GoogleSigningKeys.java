package com.codesync.backend.security;

import java.security.Key;

/** The public keys Google signs ID tokens with, looked up by the token's "kid" header. */
public interface GoogleSigningKeys {

    /** The key with this id, or null if Google does not (or no longer) use it. */
    Key find(String keyId);
}
