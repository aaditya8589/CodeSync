package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One applied edit. Stale client operations are transformed against the log entries
 * after their base revision, so the log must be written in the same transaction as the
 * document update it describes.
 */
@Entity
@Table(
    name = "document_operations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_document_operation_revision",
            columnNames = {"document_id", "revision"}
        )
    }
)
public class DocumentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // The document revision this operation produced
    @Column(nullable = false)
    private long revision;

    // TextOperation in wire format, e.g. [12,"x",-3,40]
    @Column(nullable = false, columnDefinition = "TEXT")
    private String operation;

    @Column(nullable = false, length = 255)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DocumentOperation() {
    }

    public DocumentOperation(Document document, long revision, String operation, String author) {
        this.document = document;
        this.revision = revision;
        this.operation = operation;
        this.author = author;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public long getRevision() {
        return revision;
    }

    public String getOperation() {
        return operation;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
