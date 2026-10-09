package com.codesync.backend.exception;

public class ExecutionBusyException extends RuntimeException {

    public ExecutionBusyException(String message) {
        super(message);
    }
}
