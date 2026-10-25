package com.codesync.backend.tutor;

import com.codesync.backend.arena.ArenaService;
import com.codesync.backend.arena.ProblemCatalog;
import com.codesync.backend.arena.ProblemFile;
import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.arena.ProgressResponse;
import com.codesync.backend.dto.tutor.TutorRequest;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Submission;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.exception.TutorUnavailableException;
import com.codesync.backend.match.MatchService;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tutor against the real database, with a scripted model in place of Gemini that records
 * what it was told. Every test is rolled back.
 */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class TutorIntegrationTest {

    /** Answers with whatever the test sets, and keeps the last prompt. */
    static class ScriptedProvider implements TutorProvider {
        volatile List<String> answer = List.of("Think about ", "what you have seen so far.\n");
        volatile String lastSystem;
        volatile List<TutorMessage> lastTurns;

        @Override public String name() { return "Scripted"; }
        @Override public boolean isConfigured() { return true; }

        @Override
        public void stream(String system, List<TutorMessage> turns, Consumer<String> onText) {
            lastSystem = system;
            lastTurns = turns;
            if (turns.getLast().text().contains("FAIL")) throw new TutorProviderException("The model is down");
            answer.forEach(onText);
        }
    }

    @TestConfiguration
    static class ScriptedModel {
        @Bean
        @Primary
        ScriptedProvider scriptedProvider() {
            return new ScriptedProvider();
        }
    }

    @Autowired private TutorService tutorService;
    @Autowired private ScriptedProvider model;
    @Autowired private ArenaService arenaService;
    @Autowired private RoomService roomService;
    @Autowired private MatchService matchService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private SubmissionRepository submissionRepository;

    private String member;
    private String outsider;
    private UUID roomId;
    private Document file;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = "learner-" + suffix;
        outsider = "outsider-" + suffix;
        userRepository.save(new User(member, member + "@example.com", "not-a-real-hash"));
        userRepository.save(new User(outsider, outsider + "@example.com", "not-a-real-hash"));
        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Tutor " + suffix);
        roomId = roomService.createRoom(request, member).getId();
        arenaService.attachProblem(roomId, "two-sum", member);
        file = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).get(0);
        file.setContent("// try every pair\nint main() { /* MY-CODE-MARKER */ }\n");
        documentRepository.save(file);
        model.answer = List.of("Think about ", "what you have seen so far.\n");
    }

    private TutorRequest ask(String level, String question) {
        return new TutorRequest(file.getId().toString(), level, question, List.of());
    }

    private String answer(TutorService.Prepared prepared) {
        StringBuilder out = new StringBuilder();
        tutorService.answer(prepared, out::append);
        return out.toString();
    }

    @Test
    void aHintIsAboutTheOpenFileAndTheLastVerdictButNeverHiddenTests() {
        Submission slow = new Submission(problemRepository.findBySlug("two-sum").orElseThrow(), member, roomId,
                "main.cpp", "CPP", "brute force", "TIME_LIMIT_EXCEEDED", 16, 17, 2000, null, Instant.now());
        slow.recordDetails(17, "Grows about quadratically (like O(n²))", "Doubling the input multiplied the running time by about ×4.0.");
        submissionRepository.save(slow);

        String text = answer(tutorService.prepare(roomId, ask("review", null), member));

        assertEquals("Think about what you have seen so far.\n", text);
        assertTrue(model.lastSystem.contains("MY-CODE-MARKER"));
        assertTrue(model.lastSystem.contains("time limit exceeded, 16 of 17 tests passed, first failing test: 17 (hidden)"));
        assertTrue(model.lastSystem.contains("Grows about quadratically"));
        assertTrue(model.lastSystem.contains("A good solution runs in O(n)"));
        assertTrue(model.lastTurns.getLast().text().startsWith(TutorPrompts.Level.REVIEW.request));

        ProblemFile twoSum = ProblemCatalog.loadAll().stream().filter(p -> p.slug().equals("two-sum")).findFirst().orElseThrow();
        for (ProblemFile.Test test : twoSum.tests()) {
            if (!test.sample() && test.input().length() > 20) {
                assertFalse(model.lastSystem.contains(test.input().strip()), "a hidden test reached the prompt");
            }
        }
    }

    @Test
    void codeInAnAnswerNeverReachesTheStudent() {
        model.answer = List.of("Use a map.\n```cpp\nunordered_map<int,int> seen;\n", "```\nThen look up target - x.\n");
        List<String> pieces = new ArrayList<>();
        TutorService.Outcome outcome = tutorService.answer(tutorService.prepare(roomId, ask("STEPS", null), member), pieces::add);

        String text = String.join("", pieces);
        assertTrue(outcome.codeRemoved());
        assertFalse(text.contains("unordered_map"));
        assertTrue(text.contains(CodeGuard.NOTE));
        assertTrue(text.endsWith("Then look up target - x.\n"));
    }

    @Test
    void followUpQuestionsCarryTheConversation() {
        TutorRequest request = new TutorRequest(file.getId().toString(), "ASK", "Why a hash map?", List.of(
                new TutorRequest.Turn("STUDENT", "Give me a nudge"),
                new TutorRequest.Turn("TUTOR", "What have you seen before position j?")));
        answer(tutorService.prepare(roomId, request, member));

        assertEquals(3, model.lastTurns.size());
        assertEquals(TutorMessage.Role.TUTOR, model.lastTurns.get(1).role());
        assertTrue(model.lastTurns.get(2).text().contains("Why a hash map?"));
    }

    @Test
    void onlyMembersAndRealRequests() {
        assertThrows(RoomAccessDeniedException.class, () -> tutorService.prepare(roomId, ask("NUDGE", null), outsider));
        assertThrows(InvalidRunRequestException.class, () -> tutorService.prepare(roomId, ask("ASK", " "), member));
        assertThrows(InvalidRunRequestException.class, () -> tutorService.prepare(roomId, ask("WRITE_IT_FOR_ME", null), member));
        assertThrows(InvalidRunRequestException.class,
                () -> tutorService.prepare(roomId, new TutorRequest("not-an-id", "NUDGE", null, null), member));
    }

    @Test
    void theTutorIsOffInMatchRoomsAndForProblemsOfAMatchYouPlay() {
        UUID matchRoom = UUID.fromString(matchService.create(List.of("two-sum"), 30, null, member).roomId());
        TutorUnavailableException inRoom = assertThrows(TutorUnavailableException.class,
                () -> tutorService.prepare(matchRoom, ask("NUDGE", null), member));
        assertTrue(inRoom.getMessage().contains("during a match"));

        // The same problem in an ordinary room is locked too while the match is not over
        TutorUnavailableException elsewhere = assertThrows(TutorUnavailableException.class,
                () -> tutorService.prepare(roomId, ask("NUDGE", null), member));
        assertTrue(elsewhere.getMessage().contains("in a match with this problem"));

        // Other problems are fine
        arenaService.attachProblem(roomId, "valid-brackets", member);
        answer(tutorService.prepare(roomId, ask("NUDGE", null), member));
    }

    @Test
    void aFailedAnswerDoesNotUseUpAHint() {
        int before = tutorService.status(member).leftToday();
        TutorService.Prepared prepared = tutorService.prepare(roomId, ask("ASK", "FAIL please"), member);
        assertEquals(before - 1, tutorService.status(member).leftToday());

        assertThrows(TutorProvider.TutorProviderException.class, () -> tutorService.answer(prepared, text -> {}));
        assertEquals(before, tutorService.status(member).leftToday());
    }

    @Test
    void progressCountsSolvedAndAttemptedPerDifficulty() {
        submissionRepository.save(new Submission(problemRepository.findBySlug("two-sum").orElseThrow(), member, roomId,
                "main.cpp", "CPP", "x", "WRONG_ANSWER", 3, 17, 5, null, Instant.now().minusSeconds(60)));
        submissionRepository.save(new Submission(problemRepository.findBySlug("two-sum").orElseThrow(), member, roomId,
                "main.cpp", "CPP", "x", "ACCEPTED", 17, 17, 5, null, Instant.now()));
        submissionRepository.save(new Submission(problemRepository.findBySlug("count-islands").orElseThrow(), member, roomId,
                "main.cpp", "CPP", "x", "TIME_LIMIT_EXCEEDED", 9, 16, 2000, null, Instant.now()));

        ProgressResponse progress = arenaService.progress(member);

        ProgressResponse.Level easy = progress.difficulties().get(0);
        assertEquals("Easy", easy.difficulty());
        assertEquals(1, easy.solved());
        assertEquals(0, easy.attempted());
        assertTrue(easy.total() >= 5);
        ProgressResponse.Level medium = progress.difficulties().get(1);
        assertEquals(0, medium.solved());
        assertEquals(1, medium.attempted());

        ProgressResponse.ProblemProgress twoSum = progress.problems().stream()
                .filter(p -> p.slug().equals("two-sum")).findFirst().orElseThrow();
        assertEquals("SOLVED", twoSum.status());
        assertEquals(2, twoSum.attempts());
        assertTrue(twoSum.solvedAt() != null);
        assertEquals("NEW", progress.problems().stream()
                .filter(p -> p.slug().equals("largest-rectangle")).findFirst().orElseThrow().status());
        assertEquals(0, arenaService.progress(outsider).difficulties().get(0).solved());
    }
}
