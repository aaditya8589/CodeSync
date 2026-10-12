package com.codesync.backend.dto.arena;

/** Sent to the room when someone attaches or removes the problem. */
public record RoomProblemMessage(String slug, String changedBy) {
}
