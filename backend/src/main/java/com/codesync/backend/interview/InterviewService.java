package com.codesync.backend.interview;

import com.codesync.backend.dto.interview.InterviewCreatedResponse;
import com.codesync.backend.dto.interview.InterviewEntryResponse;
import com.codesync.backend.dto.interview.InterviewResponse;
import com.codesync.backend.dto.interview.InterviewSummary;
import com.codesync.backend.dto.interview.ScorecardRequest;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Interview;
import com.codesync.backend.entity.InterviewEvent;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Room;
import com.codesync.backend.entity.RoomMember;
import com.codesync.backend.entity.Submission;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InterviewConflictException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.ProblemNotFoundException;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.InterviewEventRepository;
import com.codesync.backend.repository.InterviewRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.RoomMemberRepository;
import com.codesync.backend.repository.RoomRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Mock interviews. The interviewer creates one in its own room, shares the link and lets one
 * candidate in. The problem stays hidden until the interviewer reveals it; the interviewer
 * alone sees the problem's guide, gives hints one at a time and keeps private notes; at the end
 * they fill in a scorecard the candidate then sees. Every change takes the room's row lock, so
 * two clicks at once are applied one after the other.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    public static final List<Integer> DURATIONS = List.of(30, 45, 60);
    public static final List<String> RECOMMENDATIONS = List.of("STRONG_HIRE", "HIRE", "LEAN_HIRE", "LEAN_NO_HIRE", "NO_HIRE");
    static final int MAX_NOTE_CHARS = 2_000;
    static final int MAX_FEEDBACK_CHARS = 4_000;

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final DocumentRepository documentRepository;
    private final SubmissionRepository submissionRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewEventRepository eventRepository;
    private final Random random = new SecureRandom();

    public InterviewService(RoomService roomService, RoomRepository roomRepository,
                            RoomMemberRepository memberRepository, UserRepository userRepository,
                            ProblemRepository problemRepository, DocumentRepository documentRepository,
                            SubmissionRepository submissionRepository, InterviewRepository interviewRepository,
                            InterviewEventRepository eventRepository) {
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.problemRepository = problemRepository;
        this.documentRepository = documentRepository;
        this.submissionRepository = submissionRepository;
        this.interviewRepository = interviewRepository;
        this.eventRepository = eventRepository;
    }

    /** A new interview room in the lobby, with its creator as interviewer. */
    @Transactional
    public InterviewCreatedResponse create(String problemSlug, String difficulty, Integer durationMinutes,
                                           String meetingLink, String username) {
        if (durationMinutes == null || !DURATIONS.contains(durationMinutes)) {
            throw new InvalidRunRequestException("An interview lasts 30, 45 or 60 minutes");
        }
        String link = meetingLink(meetingLink);
        Problem problem = problemSlug != null && !problemSlug.isBlank()
                ? problemRepository.findBySlug(problemSlug.strip())
                        .orElseThrow(() -> new ProblemNotFoundException("No problem called " + problemSlug))
                : surprise(difficulty, Set.of());
        Interview interview = open(user(username), problem, durationMinutes, link);
        log.info("{} created mock interview room {} ({} min)", username, interview.getRoom().getId(), durationMinutes);
        return new InterviewCreatedResponse(interview.getRoom().getId().toString());
    }

    /** What anyone signed in sees when they open an interview link. */
    @Transactional(readOnly = true)
    public InterviewEntryResponse entry(UUID roomId, String username) {
        return entryView(interviewOf(roomId), username, now());
    }

    /** Asks to be the candidate. Only one person can be; asking again changes nothing. */
    @Transactional
    public InterviewEntryResponse requestToJoin(UUID roomId, String username) {
        user(username);
        lockRoom(roomId);
        Interview interview = interviewOf(roomId);
        Instant now = now();
        if (interview.stateAt(now) == Interview.State.ENDED) {
            throw new InterviewConflictException("This interview is over");
        }
        if (!username.equals(interview.getInterviewerUsername()) && interview.getCandidateUsername() == null) {
            interview.askToBeCandidate(username);
            log.info("{} asked to be the candidate in interview room {}", username, roomId);
        }
        return entryView(interview, username, now);
    }

    @Transactional
    public InterviewResponse approve(UUID roomId, String candidate, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        if (interview.stateAt(now) == Interview.State.ENDED) {
            throw new InterviewConflictException("This interview is over");
        }
        if (!candidate.equals(interview.getCandidateUsername())) {
            throw new InterviewConflictException(candidate + " has not asked to join");
        }
        if (!interview.isCandidateApproved()) {
            interview.approveCandidate();
            addMember(interview.getRoom(), candidate);
            log.info("{} let {} into interview room {}", username, candidate, roomId);
        }
        return view(interview, username, now);
    }

    @Transactional
    public InterviewResponse decline(UUID roomId, String candidate, String username) {
        Interview interview = interviewerAction(roomId, username);
        if (!candidate.equals(interview.getCandidateUsername())) {
            throw new InterviewConflictException(candidate + " has not asked to join");
        }
        if (interview.isCandidateApproved()) {
            throw new InterviewConflictException(candidate + " is already in the interview");
        }
        interview.clearCandidate();
        return view(interview, username, now());
    }

    @Transactional
    public InterviewResponse start(UUID roomId, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        if (interview.stateAt(now) != Interview.State.LOBBY) {
            throw new InterviewConflictException("The interview has already started");
        }
        if (interview.candidate() == null) {
            throw new InterviewConflictException("Let the candidate in first");
        }
        interview.start(now);
        log.info("{} started interview room {}", username, roomId);
        return view(interview, username, now);
    }

    /** Shows the problem to the candidate and makes it the room's problem, so Submit judges it. */
    @Transactional
    public InterviewResponse reveal(UUID roomId, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        requireRunning(interview, now);
        if (interview.getRevealedAt() == null) {
            interview.reveal(now);
            interview.getRoom().setProblem(interview.getProblem());
        }
        return view(interview, username, now);
    }

    /** Gives the next hint of the problem's ladder to the candidate. */
    @Transactional
    public InterviewResponse giveHint(UUID roomId, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        requireRunning(interview, now);
        if (interview.getRevealedAt() == null) {
            throw new InterviewConflictException("Reveal the problem before giving hints");
        }
        List<String> hints = interview.getProblem().getHints();
        if (interview.getHintsGiven() >= hints.size()) {
            throw new InterviewConflictException("All hints for this problem have been given");
        }
        eventRepository.save(new InterviewEvent(interview, InterviewEvent.Kind.HINT,
                hints.get(interview.getHintsGiven()), now));
        interview.hintGiven();
        return view(interview, username, now);
    }

    /** A private note for the interviewer, kept with the time it was written. */
    @Transactional
    public InterviewResponse addNote(UUID roomId, String text, String username) {
        Interview interview = interviewerAction(roomId, username);
        if (text == null || text.isBlank()) {
            throw new InvalidRunRequestException("Write something first");
        }
        if (text.length() > MAX_NOTE_CHARS) {
            throw new InvalidRunRequestException("A note can be up to " + MAX_NOTE_CHARS + " characters");
        }
        Instant now = now();
        eventRepository.save(new InterviewEvent(interview, InterviewEvent.Kind.NOTE, text.strip(), now));
        return view(interview, username, now);
    }

    @Transactional
    public InterviewResponse end(UUID roomId, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        requireRunning(interview, now);
        interview.endAt(now);
        log.info("{} ended interview room {}", username, roomId);
        return view(interview, username, now);
    }

    @Transactional
    public InterviewResponse score(UUID roomId, ScorecardRequest card, String username) {
        Interview interview = interviewerAction(roomId, username);
        Instant now = now();
        if (interview.stateAt(now) != Interview.State.ENDED) {
            throw new InterviewConflictException("The scorecard opens when the interview ends");
        }
        String recommendation = card.recommendation() == null ? ""
                : card.recommendation().strip().toUpperCase(Locale.ROOT);
        if (!RECOMMENDATIONS.contains(recommendation)) {
            throw new InvalidRunRequestException("Pick a recommendation");
        }
        String feedback = card.feedback() == null ? "" : card.feedback().strip();
        if (feedback.length() > MAX_FEEDBACK_CHARS) {
            throw new InvalidRunRequestException("Feedback can be up to " + MAX_FEEDBACK_CHARS + " characters");
        }
        interview.score(score(card.problemSolving()), score(card.code()), score(card.communication()),
                score(card.complexity()), recommendation, feedback, now);
        return view(interview, username, now);
    }

    /**
     * A second interview with the roles reversed and a different problem of the same difficulty,
     * both people already in. Asking again returns the same one.
     */
    @Transactional
    public InterviewCreatedResponse swap(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Interview interview = interviewOf(roomId);
        Instant now = now();
        if (interview.stateAt(now) != Interview.State.ENDED) {
            throw new InterviewConflictException("Swap roles once this interview is over");
        }
        if (interview.candidate() == null) {
            throw new InterviewConflictException("Nobody to swap with");
        }
        if (interview.getNextRoomId() != null) {
            return new InterviewCreatedResponse(interview.getNextRoomId().toString());
        }
        String newInterviewer = interview.candidate();
        String newCandidate = interview.getInterviewerUsername();
        Set<String> avoid = new HashSet<>(submissionRepository.findSolvedSlugs(newCandidate));
        avoid.add(interview.getProblem().getSlug());
        Problem problem = surprise(interview.getProblem().getDifficulty(), avoid);

        Interview next = open(user(newInterviewer), problem, interview.getDurationMinutes(), interview.getMeetingLink());
        next.askToBeCandidate(newCandidate);
        next.approveCandidate();
        addMember(next.getRoom(), newCandidate);
        interview.setNextRoomId(next.getRoom().getId());
        log.info("{} swapped roles: interview room {} follows {}", username, next.getRoom().getId(), roomId);
        return new InterviewCreatedResponse(next.getRoom().getId().toString());
    }

    /** The interview of an interview room, as the member asking sees it. */
    @Transactional(readOnly = true)
    public InterviewResponse current(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        return view(interviewOf(roomId), username, now());
    }

    /** Interviews the user took part in, newest first. */
    @Transactional(readOnly = true)
    public List<InterviewSummary> mine(String username) {
        Instant now = now();
        return interviewRepository
                .findTop50ByInterviewerUsernameOrCandidateUsernameOrderByCreatedAtDesc(username, username).stream()
                .filter(interview -> username.equals(interview.getInterviewerUsername())
                        || username.equals(interview.candidate()))
                .map(interview -> {
                    boolean interviewer = username.equals(interview.getInterviewerUsername());
                    boolean scored = interview.getScoredAt() != null;
                    Double average = scored ? (interview.getScoreProblemSolving() + interview.getScoreCode()
                            + interview.getScoreCommunication() + interview.getScoreComplexity()) / 4.0 : null;
                    // The candidate only learns the problem once it was revealed
                    boolean showProblem = interviewer || interview.getRevealedAt() != null;
                    return new InterviewSummary(interview.getRoom().getId().toString(),
                            interviewer ? "INTERVIEWER" : "CANDIDATE",
                            interviewer ? interview.candidate() : interview.getInterviewerUsername(),
                            showProblem ? interview.getProblem().getTitle() : null,
                            showProblem ? interview.getProblem().getDifficulty() : null,
                            interview.stateAt(now).name(), interview.getCreatedAt(), interview.getHintsGiven(),
                            average, scored ? interview.getRecommendation() : null);
                })
                .toList();
    }

    private Interview open(User interviewer, Problem problem, int durationMinutes, String meetingLink) {
        Room room = roomRepository.save(Room.forInterview("Mock interview: " + interviewer.getUsername(), interviewer));
        memberRepository.save(new RoomMember(room, interviewer, "OWNER"));
        documentRepository.save(new Document(room, "solution.cpp", STARTER));
        return interviewRepository.save(new Interview(room, interviewer.getUsername(), problem, durationMinutes,
                meetingLink, now()));
    }

    private Problem surprise(String difficulty, Set<String> avoid) {
        String wanted = difficulty == null ? "" : difficulty.strip();
        List<Problem> candidates = problemRepository.findAllByOrderByPositionAsc().stream()
                .filter(problem -> problem.getDifficulty().equalsIgnoreCase(wanted))
                .toList();
        if (candidates.isEmpty()) {
            throw new InvalidRunRequestException("Pick a problem, or a difficulty: Easy, Medium or Hard");
        }
        List<Problem> fresh = candidates.stream().filter(problem -> !avoid.contains(problem.getSlug())).toList();
        List<Problem> pool = fresh.isEmpty() ? candidates : fresh;
        return pool.get(random.nextInt(pool.size()));
    }

    // Only http(s) links, so the page never renders a javascript: or data: link
    static String meetingLink(String link) {
        if (link == null || link.isBlank()) return null;
        String trimmed = link.strip();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null && trimmed.length() <= 300) {
                return trimmed;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through
        }
        throw new InvalidRunRequestException("The call link must be a web address starting with https://");
    }

    private static int score(Integer value) {
        if (value == null || value < 1 || value > 4) {
            throw new InvalidRunRequestException("Every score is from 1 to 4");
        }
        return value;
    }

    private void requireRunning(Interview interview, Instant now) {
        Interview.State state = interview.stateAt(now);
        if (state == Interview.State.LOBBY) throw new InterviewConflictException("The interview has not started");
        if (state == Interview.State.ENDED) throw new InterviewConflictException("The interview is over");
    }

    private Interview interviewerAction(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Interview interview = interviewOf(roomId);
        if (!interview.getInterviewerUsername().equals(username)) {
            throw new InterviewConflictException("Only the interviewer, " + interview.getInterviewerUsername() + ", can do that");
        }
        return interview;
    }

    private Interview interviewOf(UUID roomId) {
        return interviewRepository.findFirstByRoomId(roomId)
                .orElseThrow(() -> new RoomNotFoundException("No mock interview here"));
    }

    private void addMember(Room room, String username) {
        User user = user(username);
        if (!memberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
            memberRepository.save(new RoomMember(room, user, "MEMBER"));
        }
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void lockRoom(UUID roomId) {
        roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    // Whole milliseconds, like matches: what the database stores and the browser compares
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private InterviewEntryResponse entryView(Interview interview, String username, Instant now) {
        String status;
        if (username.equals(interview.getInterviewerUsername())) status = "INTERVIEWER";
        else if (username.equals(interview.getCandidateUsername())) status = interview.isCandidateApproved() ? "CANDIDATE" : "PENDING";
        else status = interview.getCandidateUsername() == null ? "NONE" : "TAKEN";
        return new InterviewEntryResponse(interview.getRoom().getId().toString(), interview.getInterviewerUsername(),
                interview.stateAt(now).name(), interview.getDurationMinutes(), status);
    }

    private InterviewResponse view(Interview interview, String username, Instant now) {
        boolean interviewer = username.equals(interview.getInterviewerUsername());
        boolean revealed = interview.getRevealedAt() != null;
        Problem problem = interview.getProblem();

        List<InterviewEvent> events = eventRepository.findByInterviewIdOrderByCreatedAtAsc(interview.getId());
        List<InterviewResponse.Entry> hints = events.stream()
                .filter(event -> event.getKind() == InterviewEvent.Kind.HINT)
                .map(event -> entry(interview, event)).toList();
        List<InterviewResponse.Entry> notes = !interviewer ? null : events.stream()
                .filter(event -> event.getKind() == InterviewEvent.Kind.NOTE)
                .map(event -> entry(interview, event)).toList();

        List<String> ladder = problem.getHints();
        String nextHint = interviewer && interview.getHintsGiven() < ladder.size() ? ladder.get(interview.getHintsGiven()) : null;

        boolean scored = interview.getScoredAt() != null;
        InterviewResponse.Scorecard scorecard = !scored ? null : new InterviewResponse.Scorecard(
                interview.getScoreProblemSolving(), interview.getScoreCode(), interview.getScoreCommunication(),
                interview.getScoreComplexity(), interview.getRecommendation(), interview.getFeedback(),
                interview.getScoredAt());

        Interview.State state = interview.stateAt(now);
        return new InterviewResponse(
                interview.getId().toString(),
                interviewer ? "INTERVIEWER" : "CANDIDATE",
                interview.getInterviewerUsername(),
                interview.candidate(),
                interviewer && !interview.isCandidateApproved() ? interview.getCandidateUsername() : null,
                state.name(),
                interview.getDurationMinutes(),
                interview.getStartedAt(),
                interview.getEndsAt(),
                now,
                interview.getMeetingLink(),
                revealed,
                interviewer || revealed ? new InterviewResponse.ProblemRef(problem.getSlug(), problem.getTitle(),
                        problem.getDifficulty(), problem.getExpectedComplexity()) : null,
                hints,
                interviewer ? problem.getInterviewerGuide() : null,
                nextHint,
                interviewer ? ladder.size() - interview.getHintsGiven() : null,
                notes,
                lastSubmission(interview),
                scorecard,
                interview.getNextRoomId() == null ? null : interview.getNextRoomId().toString()
        );
    }

    private static InterviewResponse.Entry entry(Interview interview, InterviewEvent event) {
        Integer minute = interview.getStartedAt() == null || event.getCreatedAt().isBefore(interview.getStartedAt())
                ? null : (int) Duration.between(interview.getStartedAt(), event.getCreatedAt()).toMinutes();
        return new InterviewResponse.Entry(event.getText(), event.getCreatedAt(), minute);
    }

    // The candidate's latest submission in this room since the start
    private InterviewResponse.LastSubmission lastSubmission(Interview interview) {
        if (interview.candidate() == null || interview.getStartedAt() == null) return null;
        return submissionRepository.findFirstByProblemIdAndUsernameOrderByCreatedAtDesc(
                        interview.getProblem().getId(), interview.candidate())
                .filter(submission -> interview.getRoom().getId().equals(submission.getRoomId())
                        && !submission.getCreatedAt().isBefore(interview.getStartedAt()))
                .map(InterviewService::lastSubmission)
                .orElse(null);
    }

    private static InterviewResponse.LastSubmission lastSubmission(Submission submission) {
        return new InterviewResponse.LastSubmission(submission.getVerdict(), submission.getPassed(),
                submission.getTotal(), submission.getGrowthSummary(), submission.getCreatedAt());
    }

    private static final String STARTER = """
            // Mock interview. Talk through your idea before you code.
            // To use Python or Java, rename this file to .py or .java.
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                ios::sync_with_stdio(false);
                cin.tie(nullptr);

                return 0;
            }
            """;
}
