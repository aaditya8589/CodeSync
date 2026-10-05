package com.codesync.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "documents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_document_room_file",
            columnNames = {"room_id", "file_name"}
        )
    }
)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ColumnDefault("0")
    @Column(nullable = false)
    private long revision;

    public Document() {
    }

    public Document(Room room, String fileName, String content) {
        this.room = room;
        this.fileName = fileName;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
        this.revision++;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getRevision() {
        return revision;
    }
}