package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone who asked to play in a match. Only the host lets people in. Penalty time counts from
 * the match's start, not from being let in.
 */
@Entity
@Table(name = "match_players", uniqueConstraints = {
        @UniqueConstraint(name = "uk_match_player", columnNames = {"match_id", "username"})
})
public class MatchPlayer {

    public enum Status { PENDING, APPROVED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(nullable = false, length = 50)
    private String username;

    // When they asked (or, for the host, when the match was made)
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    // Null for players from before approvals existed: they were all in
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Status status;

    public MatchPlayer() {
    }

    public MatchPlayer(Match match, String username, Instant joinedAt, Status status) {
        this.match = match;
        this.username = username;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    public Status getStatus() {
        return status == null ? Status.APPROVED : status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getUsername() { return username; }
    public Instant getJoinedAt() { return joinedAt; }
}
