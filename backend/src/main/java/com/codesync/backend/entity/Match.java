package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A timed contest in its own match room. It waits in a lobby until the host starts it; from then
 * the server's clock decides everything: when it ends and whether a submission arrived in time.
 * Browsers only display a countdown.
 */
@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_match_room_started", columnList = "room_id, started_at"),
        @Index(name = "idx_match_origin", columnList = "origin_room_id")
})
public class Match {

    public enum State { LOBBY, RUNNING, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "host_username", nullable = false, length = 50)
    private String hostUsername;

    @Column(name = "created_at")
    private Instant createdAt;

    // Both null while in the lobby
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // The ordinary room it was created from with its Compete button, if any
    @Column(name = "origin_room_id")
    private UUID originRoomId;

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

    public Match(Room room, String hostUsername, int durationMinutes, List<Problem> problems,
                 UUID originRoomId, Instant createdAt) {
        this.room = room;
        this.hostUsername = hostUsername;
        this.durationMinutes = durationMinutes;
        this.problems = new ArrayList<>(problems);
        this.originRoomId = originRoomId;
        this.createdAt = createdAt;
    }

    public State stateAt(Instant now) {
        if (startedAt == null) return State.LOBBY;
        return now.isBefore(endsAt) ? State.RUNNING : State.ENDED;
    }

    public boolean isRunningAt(Instant now) {
        return stateAt(now) == State.RUNNING;
    }

    public void start(Instant now) {
        startedAt = now;
        endsAt = now.plus(Duration.ofMinutes(durationMinutes));
    }

    /** Ends the match early: from now on submissions no longer count and every file is revealed. */
    public void endAt(Instant now) {
        if (endsAt != null && now.isBefore(endsAt)) {
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
    public Instant getCreatedAt() { return createdAt; }
    public UUID getOriginRoomId() { return originRoomId; }
    public List<Problem> getProblems() { return problems; }

    public int getDurationMinutes() {
        if (durationMinutes != null) return durationMinutes;
        // Matches from before lobbies existed only stored their start and end
        return (int) Duration.between(startedAt, endsAt).toMinutes();
    }
}
