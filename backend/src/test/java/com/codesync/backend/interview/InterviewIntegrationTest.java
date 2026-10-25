package com.codesync.backend.interview;

import com.codesync.backend.arena.ArenaService;
import com.codesync.backend.dto.interview.InterviewResponse;
import com.codesync.backend.dto.interview.InterviewSummary;
import com.codesync.backend.dto.interview.ScorecardRequest;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Submission;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InterviewConflictException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mock interviews against the real database. Every test is rolled back. */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class InterviewIntegrationTest {

    @Autowired private InterviewService interviews;
    @Autowired private RoomService roomService;
    @Autowired private ArenaService arenaService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private DocumentRepository documentRepository;

    private String ana;
    private String ben;
    private String cy;

    @BeforeEach
    void users() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ana = "ana-" + suffix;
        ben = "ben-" + suffix;
        cy = "cy-" + suffix;
        for (String name : List.of(ana, ben, cy)) {
            userRepository.save(new User(name, name + "@example.com", "not-a-real-hash"));
        }
    }

    private UUID createWithBen(String slug) {
        UUID room = UUID.fromString(interviews.create(slug, null, 45, "https://meet.google.com/abc-defg-hij", ana).roomId());
        interviews.requestToJoin(room, ben);
        interviews.approve(room, ben, ana);
        return room;
    }

    @Test
    void theCandidateIsLetInByTheInterviewerAndOnlyOneCanAsk() {
        UUID room = UUID.fromString(interviews.create("two-sum", null, 30, null, ana).roomId());
        assertEquals("INTERVIEWER", interviews.entry(room, ana).myStatus());
        assertEquals("NONE", interviews.entry(room, ben).myStatus());
        assertThrows(RoomAccessDeniedException.class, () -> roomService.joinRoom(room, ben));

        assertEquals("PENDING", interviews.requestToJoin(room, ben).myStatus());
        assertEquals("TAKEN", interviews.requestToJoin(room, cy).myStatus());
        assertEquals(ben, interviews.current(room, ana).pendingCandidate());
        assertThrows(RoomAccessDeniedException.class, () -> interviews.current(room, ben));
        assertThrows(InterviewConflictException.class, () -> interviews.start(room, ana));

        interviews.decline(room, ben, ana);
        assertEquals("NONE", interviews.entry(room, ben).myStatus());
        interviews.requestToJoin(room, cy);
        InterviewResponse in = interviews.approve(room, cy, ana);
        assertEquals(cy, in.candidate());
        assertEquals("CANDIDATE", interviews.entry(room, cy).myStatus());
        assertEquals("CANDIDATE", interviews.current(room, cy).role());
        assertThrows(InterviewConflictException.class, () -> interviews.start(room, cy));
    }

    @Test
    void theCandidateSeesNoProblemGuideOrNotesUntilRevealed() {
        UUID room = createWithBen("two-sum");
        interviews.start(room, ana);
        interviews.addNote(room, "Asked good clarifying questions", ana);

        InterviewResponse candidate = interviews.current(room, ben);
        assertEquals("RUNNING", candidate.state());
        assertNull(candidate.problem());
        assertNull(candidate.guide());
        assertNull(candidate.nextHint());
        assertNull(candidate.hintsLeft());
        assertNull(candidate.notes());
        assertNull(roomService.getRoom(room, ben).getProblemSlug());
        assertEquals("https://meet.google.com/abc-defg-hij", candidate.meetingLink());
        assertThrows(InvalidRunRequestException.class, () -> arenaService.attachProblem(room, "two-sum", ben));

        InterviewResponse interviewer = interviews.current(room, ana);
        assertEquals("Two Sum", interviewer.problem().title());
        assertTrue(interviewer.guide().contains("Good answer"));
        assertEquals(1, interviewer.notes().size());
        assertEquals(Integer.valueOf(0), interviewer.notes().get(0).minute());

        interviews.reveal(room, ana);
        assertEquals("Two Sum", interviews.current(room, ben).problem().title());
        assertEquals("two-sum", roomService.getRoom(room, ben).getProblemSlug());
    }

    @Test
    void hintsAreGivenOneAtATimeInOrder() {
        UUID room = createWithBen("valid-brackets");
        interviews.start(room, ana);
        assertThrows(InterviewConflictException.class, () -> interviews.giveHint(room, ana));
        interviews.reveal(room, ana);

        List<String> ladder = problemRepository.findBySlug("valid-brackets").orElseThrow().getHints();
        assertEquals(ladder.get(0), interviews.current(room, ana).nextHint());
        interviews.giveHint(room, ana);
        InterviewResponse after = interviews.giveHint(room, ana);
        assertEquals(Integer.valueOf(ladder.size() - 2), after.hintsLeft());
        assertEquals(List.of(ladder.get(0), ladder.get(1)),
                interviews.current(room, ben).hintsGiven().stream().map(InterviewResponse.Entry::text).toList());
        assertThrows(InterviewConflictException.class, () -> interviews.giveHint(room, ben));
        for (int i = 2; i < ladder.size(); i++) interviews.giveHint(room, ana);
        assertThrows(InterviewConflictException.class, () -> interviews.giveHint(room, ana));
    }

    @Test
    void theScorecardOpensAtTheEndAndReachesTheCandidate() {
        UUID room = createWithBen("two-sum");
        interviews.start(room, ana);
        interviews.reveal(room, ana);
        ScorecardRequest card = new ScorecardRequest(3, 4, 2, 3, "lean_hire", "Clear explanation; test edge cases earlier.");
        assertThrows(InterviewConflictException.class, () -> interviews.score(room, card, ana));

        Problem twoSum = problemRepository.findBySlug("two-sum").orElseThrow();
        Submission submission = new Submission(twoSum, ben, room, "solution.cpp", "CPP", "x", "TIME_LIMIT_EXCEEDED",
                16, 18, 2000, null, Instant.now());
        submission.recordDetails(17, "Grows about quadratically (like O(n²))", "x4.0");
        submissionRepository.save(submission);
        assertEquals("TIME_LIMIT_EXCEEDED", interviews.current(room, ana).lastSubmission().verdict());

        interviews.end(room, ana);
        assertNull(interviews.current(room, ben).scorecard());
        assertThrows(InterviewConflictException.class, () -> interviews.score(room, card, ben));
        assertThrows(InvalidRunRequestException.class,
                () -> interviews.score(room, new ScorecardRequest(5, 1, 1, 1, "HIRE", ""), ana));
        assertThrows(InvalidRunRequestException.class,
                () -> interviews.score(room, new ScorecardRequest(1, 1, 1, 1, "MAYBE", ""), ana));

        interviews.score(room, card, ana);
        InterviewResponse.Scorecard seen = interviews.current(room, ben).scorecard();
        assertEquals(4, seen.code());
        assertEquals("LEAN_HIRE", seen.recommendation());

        InterviewSummary mine = interviews.mine(ben).get(0);
        assertEquals("CANDIDATE", mine.role());
        assertEquals(ana, mine.partner());
        assertEquals(3.0, mine.averageScore());
        assertEquals("Two Sum", mine.problemTitle());
        assertEquals("INTERVIEWER", interviews.mine(ana).get(0).role());
        assertTrue(interviews.mine(cy).isEmpty());
    }

    @Test
    void swappingRolesMakesOneFollowUpWithAnotherProblem() {
        UUID room = createWithBen("two-sum");
        assertThrows(InterviewConflictException.class, () -> interviews.swap(room, ben));
        interviews.start(room, ana);
        interviews.end(room, ana);

        UUID next = UUID.fromString(interviews.swap(room, ben).roomId());
        assertEquals(next.toString(), interviews.swap(room, ana).roomId());
        InterviewResponse swapped = interviews.current(next, ben);
        assertEquals("INTERVIEWER", swapped.role());
        assertEquals(ana, swapped.candidate());
        assertEquals("Easy", swapped.problem().difficulty());
        assertNotEquals("two-sum", swapped.problem().slug());
        assertEquals("LOBBY", swapped.state());
        assertEquals(next.toString(), interviews.current(room, ana).nextRoomId());
    }

    @Test
    void surpriseProblemsAndInputChecks() {
        UUID room = UUID.fromString(interviews.create(null, "Hard", 60, null, ana).roomId());
        assertEquals("Hard", interviews.current(room, ana).problem().difficulty());
        assertEquals(1, documentRepository.findByRoomIdOrderByFileNameAsc(room).size());

        assertThrows(InvalidRunRequestException.class, () -> interviews.create("two-sum", null, 20, null, ana));
        assertThrows(InvalidRunRequestException.class, () -> interviews.create(null, "Impossible", 30, null, ana));
        assertThrows(InvalidRunRequestException.class, () -> interviews.create("two-sum", null, 30, "javascript:alert(1)", ana));
        assertThrows(InvalidRunRequestException.class, () -> interviews.create("two-sum", null, 30, "meet.google.com/x", ana));
        assertFalse(interviews.mine(ana).isEmpty());
    }
}
