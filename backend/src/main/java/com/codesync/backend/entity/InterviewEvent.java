package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Something the interviewer did during an interview: a private note, or a hint given. */
@Entity
@Table(name = "interview_events", indexes = {
        @Index(name = "idx_interview_event_interview", columnList = "interview_id")
})
public class InterviewEvent {

    public enum Kind { NOTE, HINT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InterviewEvent() {
    }

    public InterviewEvent(Interview interview, Kind kind, String text, Instant createdAt) {
        this.interview = interview;
        this.kind = kind;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Kind getKind() { return kind; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
