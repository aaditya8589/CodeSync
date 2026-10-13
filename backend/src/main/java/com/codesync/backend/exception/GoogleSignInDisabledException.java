package com.codesync.backend.exception;

public class GoogleSignInDisabledException extends RuntimeException {

    public GoogleSignInDisabledException(String message) {
        super(message);
    }
}
