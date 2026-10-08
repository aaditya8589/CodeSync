package com.codesync.backend.dto;

/** Server to the sending user only. code is RESYNC (reload the document) or REJECTED. */
public record OperationErrorMessage(
        String clientId,
        String documentId,
        String code,
        String message
) {
}
