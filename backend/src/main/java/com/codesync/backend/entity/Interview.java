package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One mock interview in its own room: an interviewer, at most one candidate (asking, then let
 * in), one problem kept hidden until the interviewer reveals it, a clock, and a scorecard.
 */
@Entity
@Table(name = "interviews", indexes = {
        @Index(name = "idx_interview_room", columnList = "room_id")
})
public class Interview {

    public enum State { LOBBY, RUNNING, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "interviewer_username", nullable = false, length = 50)
    private String interviewerUsername;

    // Who asked to be (or is) the candidate; approved once the interviewer lets them in
    @Column(name = "candidate_username", length = 50)
    private String candidateUsername;

    @Column(name = "candidate_approved", nullable = false)
    private boolean candidateApproved;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    // A call link (Meet, Discord, ...) the interviewer shares; voice is not part of CodeSync
    @Column(name = "meeting_link", length = 300)
    private String meetingLink;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "revealed_at")
    private Instant revealedAt;

    @Column(name = "hints_given", nullable = false)
    private int hintsGiven;

    // Scorecard, filled in by the interviewer after the end
    @Column(name = "score_problem_solving")
    private Integer scoreProblemSolving;

    @Column(name = "score_code")
    private Integer scoreCode;

    @Column(name = "score_communication")
    private Integer scoreCommunication;

    @Column(name = "score_complexity")
    private Integer scoreComplexity;

    @Column(length = 20)
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "scored_at")
    private Instant scoredAt;

    // The room of the follow-up interview with roles swapped, once someone asks for it
    @Column(name = "next_room_id")
    private UUID nextRoomId;

    public Interview() {
    }

    public Interview(Room room, String interviewerUsername, Problem problem, int durationMinutes,
                     String meetingLink, Instant now) {
        this.room = room;
        this.interviewerUsername = interviewerUsername;
        this.problem = problem;
        this.durationMinutes = durationMinutes;
        this.meetingLink = meetingLink;
        this.createdAt = now;
    }

    public State stateAt(Instant now) {
        if (startedAt == null) return State.LOBBY;
        return now.isBefore(endsAt) ? State.RUNNING : State.ENDED;
    }

    public void start(Instant now) {
        startedAt = now;
        endsAt = now.plus(Duration.ofMinutes(durationMinutes));
    }

    public void endAt(Instant now) {
        endsAt = now;
    }

    public void reveal(Instant now) {
        revealedAt = now;
    }

    public void askToBeCandidate(String username) {
        candidateUsername = username;
        candidateApproved = false;
    }

    public void approveCandidate() {
        candidateApproved = true;
    }

    public void clearCandidate() {
        candidateUsername = null;
        candidateApproved = false;
    }

    public void hintGiven() {
        hintsGiven++;
    }

    public void score(int problemSolving, int code, int communication, int complexity,
                      String recommendation, String feedback, Instant now) {
        this.scoreProblemSolving = problemSolving;
        this.scoreCode = code;
        this.scoreCommunication = communication;
        this.scoreComplexity = complexity;
        this.recommendation = recommendation;
        this.feedback = feedback;
        this.scoredAt = now;
    }

    public void setNextRoomId(UUID nextRoomId) {
        this.nextRoomId = nextRoomId;
    }

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public String getInterviewerUsername() { return interviewerUsername; }
    public String getCandidateUsername() { return candidateUsername; }
    public boolean isCandidateApproved() { return candidateApproved; }
    public Problem getProblem() { return problem; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getMeetingLink() { return meetingLink; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getRevealedAt() { return revealedAt; }
    public int getHintsGiven() { return hintsGiven; }
    public Integer getScoreProblemSolving() { return scoreProblemSolving; }
    public Integer getScoreCode() { return scoreCode; }
    public Integer getScoreCommunication() { return scoreCommunication; }
    public Integer getScoreComplexity() { return scoreComplexity; }
    public String getRecommendation() { return recommendation; }
    public String getFeedback() { return feedback; }
    public Instant getScoredAt() { return scoredAt; }
    public UUID getNextRoomId() { return nextRoomId; }

    /** The approved candidate, or null. */
    public String candidate() {
        return candidateApproved ? candidateUsername : null;
    }
}
