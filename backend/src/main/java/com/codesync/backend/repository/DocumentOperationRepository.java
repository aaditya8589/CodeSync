package com.codesync.backend.repository;

import com.codesync.backend.entity.DocumentOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, UUID> {

    List<DocumentOperation> findByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(
            UUID documentId,
            long revision
    );
}
