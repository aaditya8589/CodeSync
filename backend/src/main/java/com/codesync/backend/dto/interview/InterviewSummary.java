package com.codesync.backend.dto.interview;

import java.time.Instant;

/** One interview in the user's history, for the Progress page. role: INTERVIEWER or CANDIDATE. */
public record InterviewSummary(String roomId, String role, String partner, String problemTitle,
                               String difficulty, String state, Instant createdAt, int hintsGiven,
                               Double averageScore, String recommendation) {
}
