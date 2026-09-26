package com.codesync.backend.exception;

public class AlreadyMemberException extends RuntimeException {

    public AlreadyMemberException(String message) {
        super(message);
    }
}