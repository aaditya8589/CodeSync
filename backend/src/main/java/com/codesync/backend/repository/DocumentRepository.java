package com.codesync.backend.repository;

import com.codesync.backend.entity.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByRoomIdOrderByFileNameAsc(UUID roomId);

    Optional<Document> findByIdAndRoomId(UUID id, UUID roomId);

    long countByRoomId(UUID roomId);

    // Windows and macOS treat Main.java and main.java as the same file, so the room does too
    boolean existsByRoomIdAndFileNameIgnoreCase(UUID roomId, String fileName);

    // SELECT ... FOR UPDATE: edits to the same document wait for each other, because
    // STOMP messages are handled on a thread pool and two edits could otherwise
    // both read revision N and both write N+1.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.room.id = :roomId")
    Optional<Document> findByIdAndRoomIdForUpdate(@Param("id") UUID id, @Param("roomId") UUID roomId);
}
