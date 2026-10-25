package com.codesync.backend.exception;

/** The tutor cannot help with this right now: switched off on the server, or during a match. */
public class TutorUnavailableException extends RuntimeException {

    private final boolean configured;

    public TutorUnavailableException(String message, boolean configured) {
        super(message);
        this.configured = configured;
    }

    /** False when the server has no API key, which is a 503 rather than a refusal. */
    public boolean isConfigured() {
        return configured;
    }
}
