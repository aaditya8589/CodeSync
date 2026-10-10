package com.codesync.backend.service;

import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.DuplicateFileException;
import com.codesync.backend.exception.InvalidFileNameException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the real database; every test is rolled back. */
@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
@Transactional
class DocumentCreationIntegrationTest {

    @Autowired private DocumentService documentService;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;

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
        request.setName("Files test " + suffix);
        roomId = roomService.createRoom(request, owner).getId();
    }

    private List<String> fileNames() {
        return documentService.getDocuments(roomId, owner).stream().map(DocumentResponse::getFileName).toList();
    }

    @Test
    void createdFileAppearsWithStarterCode() {
        DocumentResponse created = documentService.createDocument(roomId, "  solve.py ", owner);

        assertEquals("solve.py", created.getFileName());
        assertEquals(0, created.getRevision());
        assertTrue(created.getContent().contains("print("));
        assertEquals(List.of("main.cpp", "solution.cpp", "solve.py"), fileNames());
    }

    @Test
    void newFileCanBeEditedLikeAnyOther() {
        DocumentResponse created = documentService.createDocument(roomId, "notes.txt", owner);
        DocumentService.AppliedOperation applied = documentService.applyOperation(
                roomId, created.getId(), 0, new TextOperation().insert("hello"), owner);
        assertEquals(1, applied.revision());
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() {
        documentService.createDocument(roomId, "Main.java", owner);
        assertThrows(DuplicateFileException.class, () -> documentService.createDocument(roomId, "Main.java", owner));
        assertThrows(DuplicateFileException.class, () -> documentService.createDocument(roomId, "main.java", owner));
        assertThrows(DuplicateFileException.class, () -> documentService.createDocument(roomId, "MAIN.CPP", owner));
    }

    @Test
    void invalidNamesAreRejected() {
        for (String name : new String[]{null, "", "../x.py", "a b.py", "run.exe", "noextension"}) {
            assertThrows(InvalidFileNameException.class, () -> documentService.createDocument(roomId, name, owner), name);
        }
    }

    @Test
    void aRoomHasAtMostTwentyFiles() {
        // Every room starts with main.cpp and solution.cpp
        for (int i = 0; i < FileTemplates.MAX_FILES_PER_ROOM - 2; i++) {
            documentService.createDocument(roomId, "file" + i + ".txt", owner);
        }
        assertThrows(InvalidFileNameException.class, () -> documentService.createDocument(roomId, "one-more.txt", owner));
    }

    @Test
    void nonMembersCannotCreateFiles() {
        assertThrows(RoomAccessDeniedException.class, () -> documentService.createDocument(roomId, "x.py", outsider));
    }
}
