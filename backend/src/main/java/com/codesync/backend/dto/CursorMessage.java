package com.codesync.backend.dto;

/**
 * Client to server: "my selection in this document, as offsets into the text at this revision".
 * anchor is where the selection started, head is where the caret is; they are equal when
 * nothing is selected.
 */
public record CursorMessage(
        String documentId,
        Long revision,
        Integer anchor,
        Integer head
) {
}
