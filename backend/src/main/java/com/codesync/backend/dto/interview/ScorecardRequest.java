package com.codesync.backend.dto.interview;

/** Scores 1 to 4; recommendation STRONG_HIRE, HIRE, LEAN_HIRE, LEAN_NO_HIRE or NO_HIRE. */
public record ScorecardRequest(Integer problemSolving, Integer code, Integer communication, Integer complexity,
                               String recommendation, String feedback) {
}
