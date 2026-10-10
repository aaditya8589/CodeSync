package com.codesync.backend.history;

import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.HistoryResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InvalidRevisionException;
import com.codesync.backend.exception.RevisionNotAvailableException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.DocumentService;
import com.codesync.backend.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the real database. Every test is rolled back afterwards, so nothing is left
 * behind in the development database.
 */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class DocumentHistoryIntegrationTest {

    @Autowired private DocumentService documentService;
    @Autowired private DocumentHistoryService historyService;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;

    private String alice;
    private String bob;
    private String outsider;
    private UUID roomId;
    private Document document;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = createUser("alice-" + suffix);
        bob = createUser("bob-" + suffix);
        outsider = createUser("outsider-" + suffix);

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("History test " + suffix);
        roomId = roomService.createRoom(request, alice).getId();
        roomService.joinRoom(roomId, bob);

        document = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).get(0);
    }

    private String createUser(String username) {
        userRepository.save(new User(username, username + "@example.com", "not-a-real-hash"));
        return username;
    }

    // Applies random edits, alternating authors. Returns the text before the first edit and
    // after each one, so for a new document contents.get(r) is the text at revision r.
    private List<String> applyRandomEdits(int count) {
        Random random = new Random(42);
        long base = document.getRevision();
        List<String> contents = new ArrayList<>();
        String content = document.getContent();
        contents.add(content);

        for (int i = 0; i < count; i++) {
            int position = random.nextInt(content.length() + 1);
            int deleted = random.nextInt(Math.min(4, content.length() - position) + 1);
            TextOperation edit = new TextOperation()
                    .retain(position)
                    .delete(deleted)
                    .insert(Integer.toString(i))
                    .retain(content.length() - position - deleted);

            long revision = documentService.applyOperation(
                    roomId, document.getId(), base + i, edit, i % 2 == 0 ? alice : bob).revision();

            content = edit.apply(content);
            contents.add(content);
            assertEquals(base + i + 1, revision);
        }
        return contents;
    }

    @Test
    void historyStartsEmptyAndBeginsAtTheFirstEdit() {
        HistoryResponse before = historyService.getHistory(roomId, document.getId(), alice);
        assertNull(before.historyStartRevision());
        assertTrue(before.versions().isEmpty());

        applyRandomEdits(3);

        HistoryResponse after = historyService.getHistory(roomId, document.getId(), alice);
        assertEquals(Long.valueOf(0), after.historyStartRevision());
        assertEquals(3, after.currentRevision());
        assertEquals(1, after.versions().size());
        assertEquals(0, after.versions().get(0).fromRevision());
        assertEquals(3, after.versions().get(0).toRevision());
        assertEquals(List.of(alice, bob), after.versions().get(0).authors());
    }

    @Test
    void everyRevisionCanBeRebuiltAcrossSnapshots() {
        // 250 edits cross the snapshots at revisions 100 and 200
        List<String> contents = applyRandomEdits(250);

        for (int revision = 0; revision < contents.size(); revision++) {
            assertEquals(contents.get(revision),
                    historyService.getRevision(roomId, document.getId(), revision, bob).content(),
                    "revision " + revision);
        }
    }

    @Test
    void restoreAppliesTheOldTextAsANewRevision() {
        List<String> contents = applyRandomEdits(120);

        DocumentService.AppliedOperation restored =
                documentService.restoreRevision(roomId, document.getId(), 37L, bob);

        assertEquals(121, restored.revision());
        assertEquals(contents.get(37), restored.operation().apply(contents.get(120)));
        assertEquals(contents.get(37), documentRepository.findById(document.getId()).orElseThrow().getContent());

        // The restore is itself in the history, so it can be undone
        assertEquals(contents.get(120), historyService.getRevision(roomId, document.getId(), 120, bob).content());
        documentService.restoreRevision(roomId, document.getId(), 120L, alice);
        assertEquals(contents.get(120), documentRepository.findById(document.getId()).orElseThrow().getContent());
    }

    @Test
    void restoringTheCurrentTextChangesNothing() {
        applyRandomEdits(5);
        DocumentService.AppliedOperation result =
                documentService.restoreRevision(roomId, document.getId(), 5L, alice);

        assertTrue(result.operation().isNoop());
        assertEquals(5, result.revision());
        assertEquals(5, documentRepository.findById(document.getId()).orElseThrow().getRevision());
    }

    @Test
    void editsBeforeTheHistoryStartedCannotBeViewed() {
        // Like a document edited before this feature existed: revisions with no snapshot
        Document legacy = documentRepository.findByRoomIdOrderByFileNameAsc(roomId).get(1);
        legacy.setContent("old 1");
        legacy.setContent("old 2");
        documentRepository.saveAndFlush(legacy);
        document = legacy;

        List<String> contents = applyRandomEdits(2);  // revisions 3 and 4

        HistoryResponse history = historyService.getHistory(roomId, legacy.getId(), alice);
        assertEquals(Long.valueOf(2), history.historyStartRevision());
        assertEquals(contents.get(1), historyService.getRevision(roomId, legacy.getId(), 3, alice).content());
        assertThrows(RevisionNotAvailableException.class,
                () -> historyService.getRevision(roomId, legacy.getId(), 1, alice));
    }

    @Test
    void invalidRevisionsAreRejected() {
        applyRandomEdits(2);
        UUID id = document.getId();

        assertThrows(InvalidRevisionException.class, () -> historyService.getRevision(roomId, id, -1, alice));
        assertThrows(InvalidRevisionException.class, () -> historyService.getRevision(roomId, id, 3, alice));
        assertThrows(InvalidRevisionException.class, () -> documentService.restoreRevision(roomId, id, null, alice));
        assertThrows(InvalidRevisionException.class, () -> documentService.restoreRevision(roomId, id, 99L, alice));
    }

    @Test
    void nonMembersCannotSeeOrRestoreHistory() {
        applyRandomEdits(2);
        UUID id = document.getId();

        assertThrows(RoomAccessDeniedException.class, () -> historyService.getHistory(roomId, id, outsider));
        assertThrows(RoomAccessDeniedException.class, () -> historyService.getRevision(roomId, id, 1, outsider));
        assertThrows(RoomAccessDeniedException.class, () -> documentService.restoreRevision(roomId, id, 1L, outsider));
        assertFalse(documentRepository.findById(id).orElseThrow().getContent().isEmpty());
    }
}
