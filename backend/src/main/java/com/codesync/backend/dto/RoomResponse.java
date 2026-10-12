package com.codesync.backend.dto;

import java.time.Instant;
import java.util.UUID;

public class RoomResponse {

    private UUID id;
    private String name;
    private UUID ownerId;
    private Instant createdAt;
    private String problemSlug;

    public RoomResponse(
            UUID id,
            String name,
            UUID ownerId,
            Instant createdAt
    ) {
        this(id, name, ownerId, createdAt, null);
    }

    public RoomResponse(
            UUID id,
            String name,
            UUID ownerId,
            Instant createdAt,
            String problemSlug
    ) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.problemSlug = problemSlug;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Null when the room has no problem attached. */
    public String getProblemSlug() {
        return problemSlug;
    }
}