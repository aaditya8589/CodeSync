package com.codesync.backend.match;

import com.codesync.backend.arena.ArenaService;
import com.codesync.backend.arena.Verdict;
import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.dto.match.MatchResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.MatchConflictException;
import com.codesync.backend.execution.ExecutionService;
import com.codesync.backend.history.DocumentHistoryService;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.DocumentService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real database (problems loaded at startup); every test is rolled back. */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class MatchIntegrationTest {

    private static final String ADD_TWO_NUMBERS =
            "#include <iostream>\nint main() { long long a, b; std::cin >> a >> b; std::cout << a + b << \"\\n\"; }\n";

    @Autowired private MatchService matchService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentHistoryService historyService;
    @Autowired private ExecutionService executionService;
    @Autowired private ArenaService arenaService;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;

    private String host;
    private String rival;
    private UUID roomId;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        host = "host-" + suffix;
        rival = "rival-" + suffix;
        userRepository.save(new User(host, host + "@example.com", "not-a-real-hash"));
        userRepository.save(new User(rival, rival + "@example.com", "not-a-real-hash"));

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Match " + suffix);
        roomId = roomService.createRoom(request, host).getId();
        roomService.joinRoom(roomId, rival);
    }

    private MatchResponse start() {
        return matchService.start(roomId, List.of("add-two-numbers", "two-sum"), 30, host);
    }

    private List<String> fileNames(String username) {
        return documentService.getDocuments(roomId, username).stream().map(DocumentResponse::getFileName).toList();
    }

    private DocumentResponse privateFile(String owner, String letter) {
        return documentService.getDocuments(roomId, owner).stream()
                .filter(doc -> owner.equals(doc.getOwner()) && doc.getFileName().startsWith(letter + "-"))
                .findFirst().orElseThrow();
    }

    @Test
    void startingGivesTheHostAPrivateFilePerProblem() {
        MatchResponse match = start();

        assertTrue(match.running());
        assertTrue(match.joined());
        assertEquals(List.of("A", "B"), match.problems().stream().map(MatchResponse.MatchProblem::letter).toList());
        assertEquals(List.of(host), match.standings().stream().map(Scoreboard.Row::username).toList());

        DocumentResponse a = privateFile(host, "A");
        assertEquals("A-" + host + ".cpp", a.getFileName());
        assertEquals("add-two-numbers", a.getProblemSlug());
        assertEquals(match.endsAt(), a.getPrivateUntil());
        assertTrue(a.getContent().startsWith("// A. "), a.getContent());
    }

    @Test
    void anotherPlayerCannotReachAPrivateFileAnyWay() {
        start();
        UUID secret = privateFile(host, "A").getId();

        assertEquals(List.of("main.cpp", "solution.cpp"), fileNames(rival));
        assertThrows(DocumentNotFoundException.class, () ->
                documentService.applyOperation(roomId, secret, 0, new TextOperation().insert("x"), rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.restoreRevision(roomId, secret, 0L, rival));
        assertThrows(DocumentNotFoundException.class, () -> historyService.getHistory(roomId, secret, rival));
        assertThrows(DocumentNotFoundException.class, () -> historyService.getRevision(roomId, secret, 0L, rival));
        assertThrows(DocumentNotFoundException.class, () -> executionService.run(roomId, secret, "", rival));
        assertThrows(DocumentNotFoundException.class, () -> arenaService.submit(roomId, secret, rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.renameDocument(roomId, secret, "x.cpp", rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.deleteDocument(roomId, secret, rival));
    }

    @Test
    void editsToAPrivateFileAreMarkedForTheOwnerOnly() {
        start();
        DocumentResponse a = privateFile(host, "A");
        DocumentService.AppliedOperation applied = documentService.applyOperation(roomId, a.getId(), a.getRevision(),
                new TextOperation().retain(a.getContent().length()).insert("// mine\n"), host);
        assertEquals(host, applied.privateTo());

        Document shared = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).stream()
                .filter(doc -> doc.getFileName().equals("main.cpp")).findFirst().orElseThrow();
        DocumentService.AppliedOperation sharedEdit = documentService.applyOperation(roomId, shared.getId(),
                shared.getRevision(), new TextOperation().retain(shared.getContent().length()).insert("\n"), rival);
        assertNull(sharedEdit.privateTo());
    }

    @Test
    void ownersCanRenameButNotDeleteMatchFiles() {
        start();
        UUID a = privateFile(host, "A").getId();
        assertEquals(host, documentService.renameDocument(roomId, a, "A-" + host + ".py", host).privateTo());
        assertThrows(MatchConflictException.class, () -> documentService.deleteDocument(roomId, a, host));
    }

    @Test
    void joiningGivesTheRivalTheirOwnFilesOnce() {
        start();
        MatchResponse joined = matchService.join(roomId, rival);
        matchService.join(roomId, rival);

        assertTrue(joined.joined());
        assertEquals(2, joined.standings().size());
        assertEquals(List.of("A-" + rival + ".cpp", "B-" + rival + ".cpp", "main.cpp", "solution.cpp"), fileNames(rival));
        assertFalse(fileNames(host).contains("A-" + rival + ".cpp"));
    }

    @Test
    void oneMatchAtATimeAndSensibleSettings() {
        start();
        assertThrows(MatchConflictException.class, () -> start());

        matchService.end(roomId, host);
        assertThrows(InvalidRunRequestException.class, () -> matchService.start(roomId, List.of(), 30, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.start(roomId,
                List.of("add-two-numbers", "two-sum", "valid-brackets", "count-islands"), 30, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.start(roomId, List.of("two-sum"), 2, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.start(roomId, List.of("two-sum"), null, host));
    }

    @Test
    void onlyTheHostEndsEarlyAndThenEveryoneSeesEverything() {
        start();
        matchService.join(roomId, rival);
        assertThrows(MatchConflictException.class, () -> matchService.end(roomId, rival));

        MatchResponse ended = matchService.end(roomId, host);

        assertFalse(ended.running());
        assertTrue(fileNames(rival).contains("A-" + host + ".cpp"));
        assertTrue(fileNames(host).contains("B-" + rival + ".cpp"));
        assertThrows(MatchConflictException.class, () -> matchService.join(roomId, rival));
        // The last match stays readable with its final standings
        assertFalse(matchService.current(roomId, rival).orElseThrow().running());
    }

    @Test
    void noMatchYet() {
        assertTrue(matchService.current(roomId, host).isEmpty());
        assertThrows(MatchConflictException.class, () -> matchService.join(roomId, rival));
    }

    @Test
    void unusualUsernamesStillGiveValidFileNames() {
        assertEquals("a_b_c", MatchService.fileSafe("a b/c"));
        assertEquals(40, MatchService.fileSafe("x".repeat(60)).length());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
    void submissionsDuringTheMatchFillTheStandings() {
        start();
        Document a = documentRepository.findById(privateFile(host, "A").getId()).orElseThrow();

        a.setContent("int main() { return 0; }\n");
        documentRepository.save(a);
        ArenaService.Judged wrong = arenaService.submit(roomId, a.getId(), host);
        assertEquals(Verdict.WRONG_ANSWER, wrong.result().verdict());
        assertNotNull(wrong.matchId());

        a.setContent(ADD_TWO_NUMBERS);
        documentRepository.save(a);
        assertEquals(Verdict.ACCEPTED, arenaService.submit(roomId, a.getId(), host).result().verdict());

        Scoreboard.Row row = matchService.current(roomId, host).orElseThrow().standings().get(0);
        assertEquals(1, row.solved());
        assertEquals(Scoreboard.PENALTY_MINUTES, row.penalty());
        assertEquals(1, row.cells().get(0).rejected());

        // After the end it is practice: judged, but not counted
        matchService.end(roomId, host);
        assertNull(arenaService.submit(roomId, a.getId(), host).matchId());
    }
}
