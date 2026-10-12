package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A judged attempt. The code is kept so a verdict can always be traced back to what ran. */
@Entity
@Table(name = "submissions", indexes = {
        @Index(name = "idx_submission_problem_user", columnList = "problem_id, username")
})
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String source;

    @Column(nullable = false, length = 30)
    private String verdict;

    @Column(nullable = false)
    private int passed;

    @Column(nullable = false)
    private int total;

    @Column(name = "time_ms", nullable = false)
    private long timeMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Submission() {
    }

    public Submission(Problem problem, String username, UUID roomId, String fileName, String language,
                      String source, String verdict, int passed, int total, long timeMs) {
        this.problem = problem;
        this.username = username;
        this.roomId = roomId;
        this.fileName = fileName;
        this.language = language;
        this.source = source;
        this.verdict = verdict;
        this.passed = passed;
        this.total = total;
        this.timeMs = timeMs;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public UUID getRoomId() { return roomId; }
    public String getFileName() { return fileName; }
    public String getLanguage() { return language; }
    public String getVerdict() { return verdict; }
    public int getPassed() { return passed; }
    public int getTotal() { return total; }
    public long getTimeMs() { return timeMs; }
    public Instant getCreatedAt() { return createdAt; }
}
