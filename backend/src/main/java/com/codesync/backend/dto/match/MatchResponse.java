package com.codesync.backend.dto.match;

import com.codesync.backend.match.Scoreboard;

import java.time.Instant;
import java.util.List;

/**
 * A match room's match. startedAt and endsAt are null in the lobby. serverTime lets the browser
 * correct for its own clock when it shows the countdown. requests (people waiting to be let in)
 * is only filled in for the host.
 */
public record MatchResponse(
        String id,
        String host,
        String state,
        int durationMinutes,
        Instant startedAt,
        Instant endsAt,
        Instant serverTime,
        boolean running,
        boolean joined,
        List<MatchProblem> problems,
        List<Scoreboard.Row> standings,
        List<String> requests
) {
    public record MatchProblem(String letter, String slug, String title, String difficulty) {
    }
}
