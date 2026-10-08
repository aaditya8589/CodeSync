package com.codesync.backend.dto;

import java.util.List;

/**
 * Server to every client in the room. The sender recognizes its own clientId and treats
 * the message as the acknowledgement of its operation.
 */
public record AppliedOperationMessage(
        String documentId,
        long revision,
        List<Object> operation,
        String clientId,
        String author
) {
}
