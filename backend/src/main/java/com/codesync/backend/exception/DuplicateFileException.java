package com.codesync.backend.exception;

public class DuplicateFileException extends RuntimeException {

    public DuplicateFileException(String message) {
        super(message);
    }
}
