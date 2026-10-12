package com.codesync.backend.dto.arena;

import java.time.Instant;

public record SubmissionSummary(
        String id,
        String verdict,
        int passed,
        int total,
        long timeMs,
        String language,
        String fileName,
        String roomId,
        Instant createdAt
) {
}
