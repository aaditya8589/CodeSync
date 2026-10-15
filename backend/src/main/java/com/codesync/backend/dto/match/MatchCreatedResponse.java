package com.codesync.backend.dto.match;

/** The new match room; its share link is /match/{roomId}. */
public record MatchCreatedResponse(String roomId, String title) {
}
