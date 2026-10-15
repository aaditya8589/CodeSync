package com.codesync.backend.dto.match;

/**
 * What someone who opened a match link may see before they are let in: what the match is and
 * where they stand. myStatus is NONE, PENDING, APPROVED or DECLINED.
 */
public record MatchEntryResponse(
        String roomId,
        String title,
        String host,
        String state,
        int problemCount,
        int durationMinutes,
        String myStatus
) {
}
