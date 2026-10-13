package com.codesync.backend.dto.match;

import java.util.List;

public record StartMatchRequest(List<String> problemSlugs, Integer durationMinutes) {
}
