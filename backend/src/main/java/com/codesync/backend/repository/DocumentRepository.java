package com.codesync.backend.repository;

import com.codesync.backend.entity.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // Renaming main.cpp to Main.cpp must not count the file itself as a duplicate
    boolean existsByRoomIdAndFileNameIgnoreCaseAndIdNot(UUID roomId, String fileName, UUID id);

    // SELECT ... FOR UPDATE: edits to the same document wait for each other, because
    // STOMP messages are handled on a thread pool and two edits could otherwise
    // both read revision N and both write N+1.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.room.id = :roomId")
    Optional<Document> findByIdAndRoomIdForUpdate(@Param("id") UUID id, @Param("roomId") UUID roomId);

    // Bulk statement like the ones for the operation log and snapshots. Hibernate does not see
    // rows removed that way, so afterwards it forgets everything it was tracking; otherwise a
    // snapshot or log entry loaded earlier in the transaction would still point at this
    // document, and the next flush would refuse to continue.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Document d where d.id = :id")
    int deleteNow(@Param("id") UUID id);

    // Players' match files do not count toward the room's file limit
    long countByRoomIdAndMatchIsNull(UUID roomId);

    // Ending a match early reveals its files at once. Hibernate's in-memory copies would still
    // say "private", so they are dropped; callers re-read what they need afterwards.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Document d set d.privateUntil = :until where d.match.id = :matchId and d.privateUntil > :until")
    int revealMatchFiles(@Param("matchId") UUID matchId, @Param("until") java.time.Instant until);
}
