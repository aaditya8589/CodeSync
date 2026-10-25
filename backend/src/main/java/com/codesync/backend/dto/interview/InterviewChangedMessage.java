package com.codesync.backend.dto.interview;

/**
 * Sent on /topic/rooms/{id}/interview when something changes; each side then fetches what it is
 * allowed to see. roomId is set for SWAPPED: the room of the follow-up interview.
 */
public record InterviewChangedMessage(String event, String username, String roomId) {

    public InterviewChangedMessage(String event, String username) {
        this(event, username, null);
    }
}
