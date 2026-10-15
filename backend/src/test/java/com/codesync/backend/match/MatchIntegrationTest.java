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
import com.codesync.backend.exception.RoomAccessDeniedException;
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
    private String third;

    @BeforeEach
    void createUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        host = "host-" + suffix;
        rival = "rival-" + suffix;
        third = "third-" + suffix;
        for (String name : List.of(host, rival, third)) {
            userRepository.save(new User(name, name + "@example.com", "not-a-real-hash"));
        }
    }

    private UUID create() {
        return UUID.fromString(matchService.create(List.of("add-two-numbers", "two-sum"), 30, null, host).roomId());
    }

    /** A match room with the rival let in. */
    private UUID withRival() {
        UUID room = create();
        matchService.requestToJoin(room, rival);
        matchService.approve(room, rival, host);
        return room;
    }

    private List<String> fileNames(UUID room, String username) {
        return documentService.getDocuments(room, username).stream().map(DocumentResponse::getFileName).toList();
    }

    private DocumentResponse privateFile(UUID room, String owner, String letter) {
        return documentService.getDocuments(room, owner).stream()
                .filter(doc -> owner.equals(doc.getOwner()) && doc.getFileName().startsWith(letter + "-"))
                .findFirst().orElseThrow();
    }

    @Test
    void creatingMakesAMatchRoomWaitingInTheLobby() {
        UUID room = create();

        assertEquals("MATCH", roomService.getRoom(room, host).getKind());
        assertEquals("Match: Add Two Numbers, Two Sum", roomService.getRoom(room, host).getName());
        assertEquals(List.of(), fileNames(room, host));

        MatchResponse match = matchService.current(room, host);
        assertEquals("LOBBY", match.state());
        assertFalse(match.running());
        assertNull(match.startedAt());
        assertEquals(30, match.durationMinutes());
        assertEquals(List.of(host), match.standings().stream().map(Scoreboard.Row::username).toList());
        assertEquals("APPROVED", matchService.entry(room, host).myStatus());
    }

    @Test
    void theLinkLetsYouAskAndTheHostLetsYouIn() {
        UUID room = create();

        assertEquals("NONE", matchService.entry(room, rival).myStatus());
        assertEquals("PENDING", matchService.requestToJoin(room, rival).myStatus());
        assertEquals("PENDING", matchService.requestToJoin(room, rival).myStatus());
        assertThrows(RoomAccessDeniedException.class, () -> matchService.current(room, rival));
        assertEquals(List.of(rival), matchService.current(room, host).requests());

        MatchResponse approved = matchService.approve(room, rival, host);

        assertEquals(List.of(), approved.requests());
        assertEquals(2, approved.standings().size());
        assertTrue(matchService.current(room, rival).joined());
        assertEquals("APPROVED", matchService.entry(room, rival).myStatus());
        // Only the host sees who is waiting
        matchService.requestToJoin(room, third);
        assertEquals(List.of(), matchService.current(room, rival).requests());
    }

    @Test
    void aDeclinedRequestStaysOut() {
        UUID room = create();
        matchService.requestToJoin(room, rival);
        matchService.decline(room, rival, host);

        assertEquals("DECLINED", matchService.requestToJoin(room, rival).myStatus());
        assertThrows(RoomAccessDeniedException.class, () -> matchService.current(room, rival));
    }

    @Test
    void onlyTheHostRunsTheMatchAndNobodySkipsTheQueue() {
        UUID room = withRival();
        matchService.requestToJoin(room, third);

        assertThrows(MatchConflictException.class, () -> matchService.approve(room, third, rival));
        assertThrows(MatchConflictException.class, () -> matchService.start(room, rival));
        // Joining by room id, the way ordinary rooms work, is closed for match rooms
        assertThrows(RoomAccessDeniedException.class, () -> roomService.joinRoom(room, third));
        assertThrows(MatchConflictException.class, () -> matchService.approve(room, "never-asked", host));
    }

    @Test
    void startingGivesEveryPlayerTheirOwnPrivateFiles() {
        UUID room = withRival();
        MatchResponse started = matchService.start(room, host);

        assertEquals("RUNNING", started.state());
        assertNotNull(started.endsAt());
        assertEquals(List.of("A-" + host + ".cpp", "B-" + host + ".cpp"), fileNames(room, host));
        assertEquals(List.of("A-" + rival + ".cpp", "B-" + rival + ".cpp"), fileNames(room, rival));
        DocumentResponse a = privateFile(room, host, "A");
        assertEquals("add-two-numbers", a.getProblemSlug());
        assertEquals(started.endsAt(), a.getPrivateUntil());
        assertThrows(MatchConflictException.class, () -> matchService.start(room, host));
    }

    @Test
    void anotherPlayerCannotReachAPrivateFileAnyWay() {
        UUID room = withRival();
        matchService.start(room, host);
        UUID secret = privateFile(room, host, "A").getId();

        assertThrows(DocumentNotFoundException.class, () ->
                documentService.applyOperation(room, secret, 0, new TextOperation().insert("x"), rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.restoreRevision(room, secret, 0L, rival));
        assertThrows(DocumentNotFoundException.class, () -> historyService.getHistory(room, secret, rival));
        assertThrows(DocumentNotFoundException.class, () -> historyService.getRevision(room, secret, 0L, rival));
        assertThrows(DocumentNotFoundException.class, () -> executionService.run(room, secret, "", rival));
        assertThrows(DocumentNotFoundException.class, () -> arenaService.submit(room, secret, rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.renameDocument(room, secret, "x.cpp", rival));
        assertThrows(DocumentNotFoundException.class, () -> documentService.deleteDocument(room, secret, rival));
    }

    @Test
    void editsToAPrivateFileAreMarkedForTheOwnerOnly() {
        UUID room = withRival();
        matchService.start(room, host);
        DocumentResponse a = privateFile(room, host, "A");
        DocumentService.AppliedOperation applied = documentService.applyOperation(room, a.getId(), a.getRevision(),
                new TextOperation().retain(a.getContent().length()).insert("// mine\n"), host);
        assertEquals(host, applied.privateTo());
    }

    @Test
    void aMatchRoomHasNoSharedFilesButYouCanChangeYourLanguage() {
        UUID room = withRival();
        matchService.start(room, host);
        UUID a = privateFile(room, host, "A").getId();

        assertThrows(MatchConflictException.class, () -> documentService.createDocument(room, "notes.txt", host));
        assertThrows(MatchConflictException.class, () -> documentService.deleteDocument(room, a, host));
        assertEquals(host, documentService.renameDocument(room, a, "A-" + host + ".py", host).privateTo());
    }

    @Test
    void someoneLetInAfterTheStartGetsFilesRightAway() {
        UUID room = withRival();
        matchService.start(room, host);
        matchService.requestToJoin(room, third);
        MatchResponse match = matchService.approve(room, third, host);

        assertEquals(3, match.standings().size());
        assertEquals(List.of("A-" + third + ".cpp", "B-" + third + ".cpp"), fileNames(room, third));
    }

    @Test
    void endingRevealsEveryoneAndClosesTheDoor() {
        UUID room = withRival();
        matchService.start(room, host);
        assertThrows(MatchConflictException.class, () -> matchService.end(room, rival));

        MatchResponse ended = matchService.end(room, host);

        assertEquals("ENDED", ended.state());
        assertTrue(fileNames(room, rival).contains("A-" + host + ".cpp"));
        assertTrue(fileNames(room, host).contains("B-" + rival + ".cpp"));
        assertThrows(MatchConflictException.class, () -> matchService.requestToJoin(room, third));
        assertThrows(MatchConflictException.class, () -> matchService.end(room, host));
    }

    @Test
    void sensibleSettings() {
        assertThrows(InvalidRunRequestException.class, () -> matchService.create(List.of(), 30, null, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.create(
                List.of("add-two-numbers", "two-sum", "valid-brackets", "count-islands"), 30, null, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.create(List.of("two-sum"), 4, null, host));
        assertThrows(InvalidRunRequestException.class, () -> matchService.create(List.of("two-sum"), null, null, host));
        assertNotNull(matchService.create(List.of("two-sum"), 5, null, host).roomId());
    }

    @Test
    void anOrdinaryRoomListsTheMatchesMadeFromIt() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Study group");
        UUID studyRoom = roomService.createRoom(request, host).getId();

        String matchRoom = matchService.create(List.of("two-sum"), 10, studyRoom, host).roomId();

        assertEquals(List.of(matchRoom), matchService.madeFrom(studyRoom, host).stream().map(m -> m.roomId()).toList());
        // Only its members can make matches from it
        assertThrows(RoomAccessDeniedException.class, () -> matchService.create(List.of("two-sum"), 10, studyRoom, rival));
    }

    @Test
    void unusualUsernamesStillGiveValidFileNames() {
        assertEquals("a_b_c", MatchService.fileSafe("a b/c"));
        assertEquals(40, MatchService.fileSafe("x".repeat(60)).length());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
    void submissionsDuringTheMatchFillTheStandings() {
        UUID room = withRival();
        matchService.start(room, host);
        Document a = documentRepository.findById(privateFile(room, host, "A").getId()).orElseThrow();

        a.setContent("int main() { return 0; }\n");
        documentRepository.save(a);
        ArenaService.Judged wrong = arenaService.submit(room, a.getId(), host);
        assertEquals(Verdict.WRONG_ANSWER, wrong.result().verdict());
        assertNotNull(wrong.matchId());

        a.setContent(ADD_TWO_NUMBERS);
        documentRepository.save(a);
        assertEquals(Verdict.ACCEPTED, arenaService.submit(room, a.getId(), host).result().verdict());

        Scoreboard.Row row = matchService.current(room, host).standings().get(0);
        assertEquals(host, row.username());
        assertEquals(1, row.solved());
        assertEquals(Scoreboard.PENALTY_MINUTES, row.penalty());

        // After the end it is practice: judged, but not counted
        matchService.end(room, host);
        assertNull(arenaService.submit(room, a.getId(), host).matchId());
    }
}
