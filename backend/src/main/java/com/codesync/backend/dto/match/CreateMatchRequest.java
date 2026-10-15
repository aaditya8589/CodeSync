package com.codesync.backend.dto.match;

import java.util.List;

/** fromRoomId: the ordinary room whose Compete button made it, so its members get the link. */
public record CreateMatchRequest(List<String> problemSlugs, Integer durationMinutes, String fromRoomId) {
}
