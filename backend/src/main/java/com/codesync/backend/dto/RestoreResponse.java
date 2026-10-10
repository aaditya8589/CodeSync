package com.codesync.backend.dto;

/** The document's revision after the restore, or unchanged if it already had that content. */
public record RestoreResponse(long revision, boolean changed) {
}
