package com.codesync.backend.dto.interview;

/**
 * What an interview link shows before you are in. myStatus: INTERVIEWER, CANDIDATE (let in),
 * PENDING (asked), TAKEN (someone else is the candidate), NONE.
 */
public record InterviewEntryResponse(String roomId, String interviewer, String state, int durationMinutes,
                                     String myStatus) {
}
