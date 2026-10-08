package com.codesync.backend.dto;

import java.util.List;

/** Client to server: "apply this operation, which I made on top of baseRevision". */
public record OperationMessage(
        String documentId,
        Long baseRevision,
        List<Object> operation,
        String clientId
) {
}
