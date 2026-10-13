package com.codesync.backend.exception;

/** A match action that is not allowed in the match's current state (409). */
public class MatchConflictException extends RuntimeException {

    public MatchConflictException(String message) {
        super(message);
    }
}
