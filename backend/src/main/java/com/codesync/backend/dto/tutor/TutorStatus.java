package com.codesync.backend.dto.tutor;

/** Whether the tutor is switched on here, and how many hints this user has left. */
public record TutorStatus(boolean enabled, String provider, int leftThisHour, int leftToday) {
}
