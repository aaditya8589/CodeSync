package com.codesync.backend.repository;

import com.codesync.backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    // A match room holds exactly one match
    Optional<Match> findFirstByRoomId(UUID roomId);

    // Match rooms made with the Compete button of an ordinary room, newest first
    List<Match> findTop5ByOriginRoomIdOrderByCreatedAtDesc(UUID originRoomId);
}
