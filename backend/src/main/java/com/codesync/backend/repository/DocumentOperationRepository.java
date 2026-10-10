package com.codesync.backend.repository;

import com.codesync.backend.entity.DocumentOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, UUID> {

    List<DocumentOperation> findByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(
            UUID documentId,
            long revision
    );

    List<DocumentOperation> findByDocumentIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevisionAsc(
            UUID documentId,
            long afterRevision,
            long upToRevision
    );

    // Only the columns the history list needs, not every operation's text
    interface EditSummary {
        long getRevision();
        String getAuthor();
        Instant getCreatedAt();
    }

    List<EditSummary> findSummariesByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(
            UUID documentId,
            long revision
    );
}
