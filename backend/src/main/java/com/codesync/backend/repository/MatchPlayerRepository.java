package com.codesync.backend.repository;

import com.codesync.backend.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    List<MatchPlayer> findByMatchIdOrderByJoinedAtAsc(UUID matchId);

    boolean existsByMatchIdAndUsername(UUID matchId, String username);
}
