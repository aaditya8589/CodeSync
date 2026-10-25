package com.codesync.backend.tutor;

import java.util.List;
import java.util.function.Consumer;

/**
 * An AI model behind the tutor. Gemini today; another provider is one more implementation and a
 * setting, since nothing else in CodeSync knows which one answers.
 */
public interface TutorProvider {

    /** Shown in the UI, e.g. "Gemini". */
    String name();

    /** False when no API key is set, so the tutor shows as switched off instead of failing. */
    boolean isConfigured();

    /**
     * Streams the answer piece by piece to onText, returning when it is complete.
     *
     * @throws TutorProviderException with a message fit to show the user
     */
    void stream(String system, List<TutorMessage> turns, Consumer<String> onText);

    class TutorProviderException extends RuntimeException {
        public TutorProviderException(String message) {
            super(message);
        }
    }
}
