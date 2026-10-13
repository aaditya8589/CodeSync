package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A timed contest in a room. The server's clock decides everything: when it started, when it
 * ends, and whether a submission arrived in time. Browsers only display a countdown.
 */
@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_match_room_started", columnList = "room_id, started_at")
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "host_username", nullable = false, length = 50)
    private String hostUsername;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    // In order: the first is problem A, the second B, ...
    @ManyToMany
    @JoinTable(
            name = "match_problems",
            joinColumns = @JoinColumn(name = "match_id"),
            inverseJoinColumns = @JoinColumn(name = "problem_id")
    )
    @OrderColumn(name = "position")
    private List<Problem> problems = new ArrayList<>();

    public Match() {
    }

    public Match(Room room, String hostUsername, Instant startedAt, Instant endsAt, List<Problem> problems) {
        this.room = room;
        this.hostUsername = hostUsername;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.problems = new ArrayList<>(problems);
    }

    public boolean isRunningAt(Instant now) {
        return now.isBefore(endsAt);
    }

    /** Ends the match early: from now on submissions no longer count and every file is revealed. */
    public void endAt(Instant now) {
        if (now.isBefore(endsAt)) {
            endsAt = now;
        }
    }

    public static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public String getHostUsername() { return hostUsername; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public List<Problem> getProblems() { return problems; }
}
