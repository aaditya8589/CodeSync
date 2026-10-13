package com.codesync.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "documents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_document_room_file",
            columnNames = {"room_id", "file_name"}
        )
    }
)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ColumnDefault("0")
    @Column(nullable = false)
    private long revision;

    // The revision of the first snapshot. Null until the first edit after history was added;
    // versions older than this cannot be rebuilt.
    @Column(name = "history_start_revision")
    private Long historyStartRevision;

    // Set on a player's file in a match: until privateUntil (the match's end) only the owner may
    // see or change it. After that it is an ordinary room file, so everyone's code is revealed.
    @Column(name = "owner_username", length = 50)
    private String ownerUsername;

    @Column(name = "private_until")
    private Instant privateUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    // The match problem this file is judged against
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    public Document() {
    }

    public static Document forMatchPlayer(Room room, String fileName, String content, String owner,
                                          Match match, Problem problem) {
        Document document = new Document(room, fileName, content);
        document.ownerUsername = owner;
        document.privateUntil = match.getEndsAt();
        document.match = match;
        document.problem = problem;
        return document;
    }

    /** The one rule every way of reaching a file goes through: list, edit, history, run, submit. */
    public boolean isVisibleTo(String username, Instant now) {
        return !isPrivateAt(now) || ownerUsername.equals(username);
    }

    public boolean isPrivateAt(Instant now) {
        return privateUntil != null && now.isBefore(privateUntil);
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public Instant getPrivateUntil() {
        return privateUntil;
    }

    public Match getMatch() {
        return match;
    }

    public Problem getProblem() {
        return problem;
    }

    public Document(Room room, String fileName, String content) {
        this.room = room;
        this.fileName = fileName;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getFileName() {
        return fileName;
    }

    // Only the name changes: the id, content and revision stay, so open editors and the
    // history keep working
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
        this.revision++;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getRevision() {
        return revision;
    }

    public Long getHistoryStartRevision() {
        return historyStartRevision;
    }

    public void setHistoryStartRevision(Long historyStartRevision) {
        this.historyStartRevision = historyStartRevision;
    }
}