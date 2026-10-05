package com.codesync.backend.repository;

import com.codesync.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByRoomIdOrderByFileNameAsc(UUID roomId);

    Optional<Document> findByIdAndRoomId(UUID id, UUID roomId);
}