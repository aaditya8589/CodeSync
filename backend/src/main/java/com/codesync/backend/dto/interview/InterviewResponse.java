package com.codesync.backend.dto.interview;

import java.time.Instant;
import java.util.List;

/**
 * An interview as one of its two people sees it. The interviewer-only fields (pendingCandidate,
 * guide, nextHint, hintsLeft, notes) are null for the candidate, and so is the problem until
 * it is revealed. The scorecard reaches the candidate once it has been filled in.
 */
public record InterviewResponse(
        String id,
        String role,
        String interviewer,
        String candidate,
        String pendingCandidate,
        String state,
        int durationMinutes,
        Instant startedAt,
        Instant endsAt,
        Instant serverTime,
        String meetingLink,
        boolean revealed,
        ProblemRef problem,
        List<Entry> hintsGiven,
        String guide,
        String nextHint,
        Integer hintsLeft,
        List<Entry> notes,
        LastSubmission lastSubmission,
        Scorecard scorecard,
        String nextRoomId
) {

    public record ProblemRef(String slug, String title, String difficulty, String expectedComplexity) {
    }

    /** minute: minutes since the start, for "14:20 ..." in notes; null before the start. */
    public record Entry(String text, Instant at, Integer minute) {
    }

    public record LastSubmission(String verdict, int passed, int total, String growthSummary, Instant at) {
    }

    public record Scorecard(int problemSolving, int code, int communication, int complexity,
                            String recommendation, String feedback, Instant at) {
    }
}
