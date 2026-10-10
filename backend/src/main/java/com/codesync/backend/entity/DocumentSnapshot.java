package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The full text of a document at one revision. The operation log only records how many
 * characters an edit deleted, not which, so old versions are rebuilt forwards: the nearest
 * snapshot at or before a revision, plus the logged operations after it.
 */
@Entity
@Table(
    name = "document_snapshots",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_document_snapshot_revision",
            columnNames = {"document_id", "revision"}
        )
    }
)
public class DocumentSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private long revision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DocumentSnapshot() {
    }

    public DocumentSnapshot(Document document, long revision, String content) {
        this.document = document;
        this.revision = revision;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public long getRevision() {
        return revision;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
