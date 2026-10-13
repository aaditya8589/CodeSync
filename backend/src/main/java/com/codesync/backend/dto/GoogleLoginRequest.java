package com.codesync.backend.dto;

/** The ID token Google's sign-in button hands the browser (Google calls it "credential"). */
public record GoogleLoginRequest(String credential) {
}
