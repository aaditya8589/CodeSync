package com.codesync.backend.dto;

import java.time.Instant;
import java.util.UUID;

public class DocumentResponse {

    private UUID id;
    private String fileName;
    private String content;
    private Instant updatedAt;
    private long revision;
    // Match files only: whose it is, until when only they can see it, and the problem it solves
    private String owner;
    private Instant privateUntil;
    private String problemSlug;

    public DocumentResponse(UUID id, String fileName, String content, Instant updatedAt, long revision) {
        this.id = id;
        this.fileName = fileName;
        this.content = content;
        this.updatedAt = updatedAt;
        this.revision = revision;
    }

    public DocumentResponse(UUID id, String fileName, String content, Instant updatedAt, long revision,
                            String owner, Instant privateUntil, String problemSlug) {
        this(id, fileName, content, updatedAt, revision);
        this.owner = owner;
        this.privateUntil = privateUntil;
        this.problemSlug = problemSlug;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRevision() { return revision; }
    public String getOwner() { return owner; }
    public Instant getPrivateUntil() { return privateUntil; }
    public String getProblemSlug() { return problemSlug; }
}