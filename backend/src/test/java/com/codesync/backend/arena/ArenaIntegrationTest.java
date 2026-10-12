package com.codesync.backend.arena;

import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.arena.ProblemDetail;
import com.codesync.backend.dto.arena.ProblemSummary;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.ProblemNotFoundException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the real database; the problems are loaded at startup by ProblemCatalog.
 * Every test is rolled back.
 */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class ArenaIntegrationTest {

    @Autowired private ArenaService arenaService;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;

    private String member;
    private String outsider;
    private UUID roomId;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = "solver-" + suffix;
        outsider = "outsider-" + suffix;
        userRepository.save(new User(member, member + "@example.com", "not-a-real-hash"));
        userRepository.save(new User(outsider, outsider + "@example.com", "not-a-real-hash"));

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Arena " + suffix);
        roomId = roomService.createRoom(request, member).getId();
    }

    @Test
    void bundledProblemsAreListedInOrder() {
        List<ProblemSummary> problems = arenaService.listProblems(member);
        assertEquals("add-two-numbers", problems.get(0).slug());
        assertTrue(problems.size() >= 6);
        assertFalse(problems.get(0).solved());
    }

    @Test
    void problemDetailShowsExamplesButNotHiddenTests() {
        ProblemDetail detail = arenaService.getProblem("two-sum", member);
        assertEquals("Two Sum", detail.title());
        assertEquals(2, detail.examples().size());
        assertEquals("0 1\n", detail.examples().get(0).output());
        assertTrue(detail.testCount() > detail.examples().size());
        assertThrows(ProblemNotFoundException.class, () -> arenaService.getProblem("no-such-problem", member));
    }

    @Test
    void problemCanBeAttachedAndRemovedByMembersOnly() {
        assertEquals("two-sum", arenaService.attachProblem(roomId, "two-sum", member));
        assertEquals("two-sum", roomService.getRoom(roomId, member).getProblemSlug());

        assertThrows(RoomAccessDeniedException.class, () -> arenaService.attachProblem(roomId, "two-sum", outsider));

        assertNull(arenaService.attachProblem(roomId, null, member));
        assertNull(roomService.getRoom(roomId, member).getProblemSlug());
    }

    @Test
    void submittingNeedsAProblemAndMembership() {
        UUID documentId = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).get(0).getId();
        assertThrows(InvalidRunRequestException.class, () -> arenaService.submit(roomId, documentId, member));
        assertThrows(RoomAccessDeniedException.class, () -> arenaService.submit(roomId, documentId, outsider));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
    void acceptedSubmissionIsSavedAndMarksTheProblemSolved() {
        arenaService.attachProblem(roomId, "add-two-numbers", member);
        Document main = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).get(0);
        main.setContent("#include <iostream>\nint main() { long long a, b; std::cin >> a >> b; std::cout << a + b << \"\\n\"; }\n");
        documentRepository.save(main);

        ArenaService.Judged judged = arenaService.submit(roomId, main.getId(), member);

        assertEquals(Verdict.ACCEPTED, judged.result().verdict());
        assertEquals("main.cpp", judged.fileName());
        assertTrue(arenaService.getProblem("add-two-numbers", member).solved());
        assertEquals("ACCEPTED", arenaService.mySubmissions("add-two-numbers", member).get(0).verdict());
        assertFalse(arenaService.getProblem("add-two-numbers", outsider).solved());
    }
}
