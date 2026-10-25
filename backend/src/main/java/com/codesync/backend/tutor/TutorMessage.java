package com.codesync.backend.tutor;

/** One turn of a conversation with the tutor. */
public record TutorMessage(Role role, String text) {

    public enum Role { STUDENT, TUTOR }
}
