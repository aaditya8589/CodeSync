package com.codesync.backend.security;

/** What a verified Google ID token says about the person signing in. */
public record GoogleAccount(String subject, String email, boolean emailVerified, String name) {
}
