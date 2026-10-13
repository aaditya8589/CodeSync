package com.codesync.backend.repository;

import com.codesync.backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    // The room's current match, or its last one (kept so the final standings stay visible)
    Optional<Match> findFirstByRoomIdOrderByStartedAtDesc(UUID roomId);
}
