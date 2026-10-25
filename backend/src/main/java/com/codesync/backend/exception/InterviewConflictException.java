package com.codesync.backend.exception;

/** An interview action that does not fit its state or the user's role. */
public class InterviewConflictException extends RuntimeException {

    public InterviewConflictException(String message) {
        super(message);
    }
}
