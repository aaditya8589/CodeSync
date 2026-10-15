package com.codesync.backend.dto.match;

/**
 * Tells a room to fetch the match again (REQUESTED, APPROVED, STARTED, SUBMITTED, ENDED), or, in
 * the ordinary room a match was made from, that a match room exists (CREATED, with its id).
 */
public record MatchChangedMessage(String event, String username, String matchRoomId) {

    public MatchChangedMessage(String event, String username) {
        this(event, username, null);
    }
}
