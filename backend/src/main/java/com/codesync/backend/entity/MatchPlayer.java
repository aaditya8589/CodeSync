package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Someone who joined a match. Penalty time still counts from the match's start, not from joining. */
@Entity
@Table(name = "match_players", uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_player", columnNames = {"match_id", "username"})
})
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public MatchPlayer() {
    }

    public MatchPlayer(Match match, String username, Instant joinedAt) {
        this.match = match;
        this.username = username;
        this.joinedAt = joinedAt;
    }

    public String getUsername() { return username; }
    public Instant getJoinedAt() { return joinedAt; }
}
