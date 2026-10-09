package com.codesync.backend.exception;

public class InvalidRunRequestException extends RuntimeException {

    public InvalidRunRequestException(String message) {
        super(message);
    }
}
