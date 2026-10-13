package com.codesync.backend.dto.match;

/** Tells the room to fetch the match again (started, joined, submission judged, ended). */
public record MatchChangedMessage(String event, String username) {
}
