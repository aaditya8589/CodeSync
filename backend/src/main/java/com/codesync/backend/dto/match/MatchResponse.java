package com.codesync.backend.dto.match;

import com.codesync.backend.match.Scoreboard;

import java.time.Instant;
import java.util.List;

/**
 * The room's current or last match. serverTime lets the browser correct for its own clock
 * being off when it shows the countdown.
 */
public record MatchResponse(
        String id,
        String host,
        Instant startedAt,
        Instant endsAt,
        Instant serverTime,
        boolean running,
        boolean joined,
        List<MatchProblem> problems,
        List<Scoreboard.Row> standings
) {
    public record MatchProblem(String letter, String slug, String title, String difficulty) {
    }
}
