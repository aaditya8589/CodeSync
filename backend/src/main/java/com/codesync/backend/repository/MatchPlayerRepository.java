package com.codesync.backend.repository;

import com.codesync.backend.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    List<MatchPlayer> findByMatchIdOrderByJoinedAtAsc(UUID matchId);

    Optional<MatchPlayer> findByMatchIdAndUsername(UUID matchId, String username);

    /**
     * Whether the user plays (or waits in the lobby of) a match that includes this problem and
     * has not ended. Lobbies older than lobbySince no longer count, so an abandoned lobby does not
     * lock a problem forever.
     */
    @Query("select count(player) > 0 from MatchPlayer player join player.match m join m.problems problem"
            + " where player.username = :username and problem.id = :problemId"
            + " and (player.status is null or player.status <> :declined)"
            + " and ((m.endsAt is null and m.createdAt > :lobbySince) or m.endsAt > :now)")
    boolean inUnfinishedMatchWith(@Param("username") String username, @Param("problemId") UUID problemId,
                                  @Param("declined") MatchPlayer.Status declined,
                                  @Param("now") Instant now, @Param("lobbySince") Instant lobbySince);
}
