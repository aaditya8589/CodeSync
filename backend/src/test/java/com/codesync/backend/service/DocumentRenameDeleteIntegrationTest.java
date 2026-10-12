package com.codesync.backend.service;

import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.DuplicateFileException;
import com.codesync.backend.exception.InvalidFileNameException;
import com.codesync.backend.exception.LastFileException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentOperationRepository;
import com.codesync.backend.repository.DocumentSnapshotRepository;
import com.codesync.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real database; every test is rolled back. */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class DocumentRenameDeleteIntegrationTest {

    @Autowired private DocumentService documentService;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentOperationRepository operationRepository;
    @Autowired private DocumentSnapshotRepository snapshotRepository;

    private String owner;
    private String outsider;
    private UUID roomId;

    @BeforeEach
    void createRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = "owner-" + suffix;
        outsider = "outsider-" + suffix;
        userRepository.save(new User(owner, owner + "@example.com", "not-a-real-hash"));
        userRepository.save(new User(outsider, outsider + "@example.com", "not-a-real-hash"));

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Rename/delete test " + suffix);
        roomId = roomService.createRoom(request, owner).getId();
    }

    private List<DocumentResponse> documents() {
        return documentService.getDocuments(roomId, owner);
    }

    private DocumentResponse file(String name) {
        return documents().stream().filter(doc -> doc.getFileName().equals(name)).findFirst().orElseThrow();
    }

    private List<String> fileNames() {
        return documents().stream().map(DocumentResponse::getFileName).toList();
    }

    @Test
    void renameKeepsIdTextAndRevisionSoEditingContinues() {
        DocumentResponse before = file("solution.cpp");
        documentService.applyOperation(roomId, before.getId(), before.getRevision(),
                new TextOperation().insert("// x\n").retain(before.getContent().length()), owner);

        DocumentService.RenamedDocument renamed =
                documentService.renameDocument(roomId, before.getId(), " solve.py ", owner);

        assertEquals("solution.cpp", renamed.previousName());
        assertEquals("solve.py", renamed.document().getFileName());
        assertEquals(before.getId(), renamed.document().getId());
        assertEquals(before.getRevision() + 1, renamed.document().getRevision());
        assertEquals("// x\n" + before.getContent(), renamed.document().getContent());
        assertEquals(List.of("main.cpp", "solve.py"), fileNames());

        // An edit based on the revision before the rename still applies
        DocumentService.AppliedOperation applied = documentService.applyOperation(roomId, before.getId(),
                before.getRevision() + 1, new TextOperation().retain(renamed.document().getContent().length()).insert("!"), owner);
        assertEquals(before.getRevision() + 2, applied.revision());
    }

    @Test
    void changingOnlyTheCaseIsAllowed() {
        DocumentResponse main = file("main.cpp");
        assertEquals("Main.cpp", documentService.renameDocument(roomId, main.getId(), "Main.cpp", owner).document().getFileName());
    }

    @Test
    void renamingToTheSameNameChangesNothing() {
        DocumentResponse main = file("main.cpp");
        DocumentService.RenamedDocument renamed = documentService.renameDocument(roomId, main.getId(), "main.cpp", owner);
        assertEquals("main.cpp", renamed.previousName());
        assertEquals("main.cpp", renamed.document().getFileName());
    }

    @Test
    void renameRejectsTakenAndInvalidNames() {
        UUID main = file("main.cpp").getId();
        assertThrows(DuplicateFileException.class, () -> documentService.renameDocument(roomId, main, "SOLUTION.cpp", owner));
        for (String name : new String[]{null, "", "../x.py", "a b.py", "run.exe"}) {
            assertThrows(InvalidFileNameException.class, () -> documentService.renameDocument(roomId, main, name, owner), name);
        }
        assertEquals(List.of("main.cpp", "solution.cpp"), fileNames());
    }

    @Test
    void deleteRemovesTheFileWithItsHistory() {
        DocumentResponse doomed = file("solution.cpp");
        documentService.applyOperation(roomId, doomed.getId(), doomed.getRevision(),
                new TextOperation().retain(doomed.getContent().length()).insert("\n"), owner);
        assertFalse(operationRepository.findByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(doomed.getId(), -1).isEmpty());
        assertTrue(snapshotRepository.findFirstByDocumentIdAndRevisionLessThanEqualOrderByRevisionDesc(doomed.getId(), Long.MAX_VALUE).isPresent());

        assertEquals("solution.cpp", documentService.deleteDocument(roomId, doomed.getId(), owner));

        assertEquals(List.of("main.cpp"), fileNames());
        assertTrue(operationRepository.findByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(doomed.getId(), -1).isEmpty());
        assertTrue(snapshotRepository.findFirstByDocumentIdAndRevisionLessThanEqualOrderByRevisionDesc(doomed.getId(), Long.MAX_VALUE).isEmpty());

        // A tab that has not heard about the deletion yet gets "not found" and reloads
        assertThrows(DocumentNotFoundException.class, () -> documentService.applyOperation(
                roomId, doomed.getId(), doomed.getRevision() + 1, new TextOperation().insert("x"), owner));
    }

    @Test
    void theLastFileCannotBeDeleted() {
        documentService.deleteDocument(roomId, file("solution.cpp").getId(), owner);
        UUID last = file("main.cpp").getId();
        assertThrows(LastFileException.class, () -> documentService.deleteDocument(roomId, last, owner));
        assertEquals(List.of("main.cpp"), fileNames());
    }

    @Test
    void aDeletedNameCanBeUsedAgain() {
        documentService.deleteDocument(roomId, file("solution.cpp").getId(), owner);
        DocumentResponse again = documentService.createDocument(roomId, "solution.cpp", owner);
        assertEquals(0, again.getRevision());
    }

    @Test
    void outsidersAndOtherRoomsAreRejected() {
        UUID main = file("main.cpp").getId();
        assertThrows(RoomAccessDeniedException.class, () -> documentService.renameDocument(roomId, main, "x.py", outsider));
        assertThrows(RoomAccessDeniedException.class, () -> documentService.deleteDocument(roomId, main, outsider));

        CreateRoomRequest request = new CreateRoomRequest();
        request.setName("Other room");
        UUID otherRoom = roomService.createRoom(request, owner).getId();
        assertThrows(DocumentNotFoundException.class, () -> documentService.renameDocument(otherRoom, main, "x.py", owner));
        assertThrows(DocumentNotFoundException.class, () -> documentService.deleteDocument(otherRoom, main, owner));
        assertEquals(List.of("main.cpp", "solution.cpp"), fileNames());
    }
}
