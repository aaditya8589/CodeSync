package com.codesync.backend.dto.arena;

import java.time.Instant;
import java.util.List;

/** One user's progress through the problems: totals per difficulty, then every problem. */
public record ProgressResponse(List<Level> difficulties, List<ProblemProgress> problems) {

    public record Level(String difficulty, int solved, int attempted, int total) {
    }

    /** status: SOLVED, ATTEMPTED (submitted, never accepted) or NEW. */
    public record ProblemProgress(String slug, String title, String difficulty, String status,
                                  int attempts, Instant solvedAt) {
    }
}
