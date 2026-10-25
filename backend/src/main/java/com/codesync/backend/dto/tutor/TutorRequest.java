package com.codesync.backend.dto.tutor;

import java.util.List;

/**
 * level: NUDGE, APPROACH, STEPS, REVIEW or ASK. history: the conversation so far as the browser
 * shows it (role STUDENT or TUTOR), so a follow-up question has its context.
 */
public record TutorRequest(String documentId, String level, String question, List<Turn> history) {

    public record Turn(String role, String text) {
    }
}
