package com.codesync.backend.exception;

public class RoomAccessDeniedException extends RuntimeException {

    public RoomAccessDeniedException(String message) {
        super(message);
    }
}