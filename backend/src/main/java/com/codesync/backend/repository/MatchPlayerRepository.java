package com.codesync.backend.repository;

import com.codesync.backend.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    List<MatchPlayer> findByMatchIdOrderByJoinedAtAsc(UUID matchId);

    Optional<MatchPlayer> findByMatchIdAndUsername(UUID matchId, String username);
}
