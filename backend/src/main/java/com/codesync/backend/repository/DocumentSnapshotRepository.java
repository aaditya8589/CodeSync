package com.codesync.backend.repository;

import com.codesync.backend.entity.DocumentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshot, UUID> {

    Optional<DocumentSnapshot> findFirstByDocumentIdAndRevisionLessThanEqualOrderByRevisionDesc(
            UUID documentId,
            long revision
    );

    // One statement instead of loading every row just to delete it. Rows saved earlier in the
    // same transaction are written first, so they are deleted too.
    @Modifying(flushAutomatically = true)
    @Query("delete from DocumentSnapshot e where e.document.id = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
