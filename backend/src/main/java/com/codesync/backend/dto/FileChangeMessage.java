package com.codesync.backend.dto;

/**
 * Sent to the room when a file is created, renamed or deleted. A rename only changes the
 * name, so tabs update it in place; a creation or deletion changes the set of files, so tabs
 * reload the list.
 */
public record FileChangeMessage(
        Type type,
        String documentId,
        String fileName,
        // The name before a rename or deletion; null for a creation
        String previousName,
        String changedBy
) {
    public enum Type { CREATED, RENAMED, DELETED }
}
