package com.codesync.backend.dto.arena;

/** Sent to the room after a submission is judged, so everyone sees how it went. */
public record SubmissionMessage(
        String username,
        String problemTitle,
        String fileName,
        String verdict,
        int passed,
        int total,
        Integer failedTest
) {
}
