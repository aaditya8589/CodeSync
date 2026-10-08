package com.codesync.backend.exception;

public class ResyncRequiredException extends RuntimeException {

    public ResyncRequiredException(String message) {
        super(message);
    }
}
