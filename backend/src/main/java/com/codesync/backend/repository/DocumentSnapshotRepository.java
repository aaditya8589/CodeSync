package com.codesync.backend.repository;

import com.codesync.backend.entity.DocumentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshot, UUID> {

    Optional<DocumentSnapshot> findFirstByDocumentIdAndRevisionLessThanEqualOrderByRevisionDesc(
            UUID documentId,
            long revision
    );
}
