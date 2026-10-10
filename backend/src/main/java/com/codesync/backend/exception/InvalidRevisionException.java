package com.codesync.backend.exception;

public class InvalidRevisionException extends RuntimeException {

    public InvalidRevisionException(String message) {
        super(message);
    }
}
