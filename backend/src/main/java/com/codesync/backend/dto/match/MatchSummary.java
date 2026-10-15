package com.codesync.backend.dto.match;

/** A match room made from an ordinary room, listed there with its link. */
public record MatchSummary(String roomId, String title, String host, String state) {
}
