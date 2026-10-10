package com.codesync.backend.dto;

/** Server to the room. clientId and username come from the server, never from the sender. */
public record RemoteCursorMessage(
        String clientId,
        String username,
        String documentId,
        long revision,
        int anchor,
        int head
) {
}
