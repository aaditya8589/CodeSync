package com.codesync.backend.dto.interview;

/** problemSlug, or difficulty ("Easy", "Medium", "Hard") for a surprise problem. */
public record CreateInterviewRequest(String problemSlug, String difficulty, Integer durationMinutes,
                                     String meetingLink) {
}
