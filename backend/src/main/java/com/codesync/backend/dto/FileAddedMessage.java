package com.codesync.backend.dto;

/** Sent to the room when a file is created, so every open tab reloads its file list. */
public record FileAddedMessage(String documentId, String fileName, String createdBy) {
}
