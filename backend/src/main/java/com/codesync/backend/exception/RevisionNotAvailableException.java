package com.codesync.backend.exception;

public class RevisionNotAvailableException extends RuntimeException {

    public RevisionNotAvailableException(String message) {
        super(message);
    }
}
