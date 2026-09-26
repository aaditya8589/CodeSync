package com.codesync.backend.dto;

import java.time.Instant;
import java.util.UUID;

public class RoomResponse {

    private UUID id;
    private String name;
    private UUID ownerId;
    private Instant createdAt;

    public RoomResponse(
            UUID id,
            String name,
            UUID ownerId,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
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
}