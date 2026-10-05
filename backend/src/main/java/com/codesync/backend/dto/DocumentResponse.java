package com.codesync.backend.dto;

import java.time.Instant;
import java.util.UUID;

public class DocumentResponse {

    private UUID id;
    private String fileName;
    private String content;
    private Instant updatedAt;
    private long revision;

    public DocumentResponse(UUID id, String fileName, String content, Instant updatedAt, long revision) {
        this.id = id;
        this.fileName = fileName;
        this.content = content;
        this.updatedAt = updatedAt;
        this.revision = revision;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRevision() { return revision; }
}