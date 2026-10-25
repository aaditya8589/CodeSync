package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // The problem the room is working on, if any; everyone in the room sees it next to the code
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    // MATCH rooms exist for one contest: only the players' own files, joined through the host.
    // INTERVIEW rooms hold one mock interview, joined through the interviewer's link.
    // Null for every room made before match rooms existed, which are all ordinary rooms.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Kind kind;

    public enum Kind { NORMAL, MATCH, INTERVIEW }

    public Room() {
    }

    public Room(String name, User owner) {
        this.name = name;
        this.owner = owner;
        this.createdAt = Instant.now();
        this.kind = Kind.NORMAL;
    }

    public static Room forMatch(String name, User owner) {
        Room room = new Room(name, owner);
        room.kind = Kind.MATCH;
        return room;
    }

    public static Room forInterview(String name, User owner) {
        Room room = new Room(name, owner);
        room.kind = Kind.INTERVIEW;
        return room;
    }

    public boolean isMatchRoom() {
        return kind == Kind.MATCH;
    }

    public boolean isInterviewRoom() {
        return kind == Kind.INTERVIEW;
    }

    public Kind getKind() {
        return kind == null ? Kind.NORMAL : kind;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }
}