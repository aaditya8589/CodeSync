package com.codesync.backend.service;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.DocumentOperation;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.DuplicateFileException;
import com.codesync.backend.exception.InvalidFileNameException;
import com.codesync.backend.exception.LastFileException;
import com.codesync.backend.exception.MatchConflictException;
import com.codesync.backend.entity.Room;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.exception.InvalidRevisionException;
import com.codesync.backend.history.DocumentHistoryService;
import com.codesync.backend.exception.ResyncRequiredException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentOperationRepository;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.DocumentSnapshotRepository;
import com.codesync.backend.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public static final int MAX_DOCUMENT_LENGTH = 500_000;

    /** privateTo: the owner when the document is a player's private match file, so only they get it. */
    public record AppliedOperation(UUID documentId, long revision, TextOperation operation, String privateTo) {
    }

    private final DocumentRepository documentRepository;
    private final DocumentOperationRepository documentOperationRepository;
    private final RoomService roomService;
    private final DocumentHistoryService historyService;
    private final RoomRepository roomRepository;
    private final DocumentSnapshotRepository documentSnapshotRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentOperationRepository documentOperationRepository,
            RoomService roomService,
            DocumentHistoryService historyService,
            RoomRepository roomRepository,
            DocumentSnapshotRepository documentSnapshotRepository
    ) {
        this.documentRepository = documentRepository;
        this.documentOperationRepository = documentOperationRepository;
        this.roomService = roomService;
        this.historyService = historyService;
        this.roomRepository = roomRepository;
        this.documentSnapshotRepository = documentSnapshotRepository;
    }

    @Transactional
    public DocumentResponse createDocument(UUID roomId, String fileName, String username) {
        roomService.checkRoomAccess(roomId, username);
        Room room = lockRoom(roomId);

        String name = validName(fileName);
        if (documentRepository.countByRoomIdAndMatchIsNull(roomId) >= FileTemplates.MAX_FILES_PER_ROOM) {
            throw new InvalidFileNameException(
                    "A room can have at most " + FileTemplates.MAX_FILES_PER_ROOM + " files");
        }
        if (documentRepository.existsByRoomIdAndFileNameIgnoreCase(roomId, name)) {
            throw duplicate(name);
        }

        Document document;
        try {
            document = documentRepository.saveAndFlush(new Document(room, name, FileTemplates.starterContent(name)));
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint is the last line of defence if the checks above were bypassed
            throw duplicate(name);
        }

        log.info("{} created {} in room {}", username, name, roomId);
        return toResponse(document);
    }

    public record RenamedDocument(DocumentResponse document, String previousName, String privateTo) {
    }

    /**
     * Changes only the name. The id, text and revision stay the same, so edits in flight are
     * unaffected and the history carries over. A new extension changes the language it runs as.
     */
    @Transactional
    public RenamedDocument renameDocument(UUID roomId, UUID documentId, String fileName, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Document document = findDocumentForUpdate(roomId, documentId, username);
        String privateTo = privateTo(document);

        String name = validName(fileName);
        String previous = document.getFileName();
        if (name.equals(previous)) {
            return new RenamedDocument(toResponse(document), previous, privateTo);
        }
        if (documentRepository.existsByRoomIdAndFileNameIgnoreCaseAndIdNot(roomId, name, documentId)) {
            throw duplicate(name);
        }

        document.setFileName(name);
        try {
            documentRepository.saveAndFlush(document);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate(name);
        }

        log.info("{} renamed {} to {} in room {}", username, previous, name, roomId);
        return new RenamedDocument(toResponse(document), previous, privateTo);
    }

    /**
     * Deletes the file with its operation log and snapshots. The document row is locked first,
     * so an edit that is being applied finishes before, and one that arrives later finds no
     * document and makes its tab reload. A room always keeps at least one file.
     *
     * @return the name the file had
     */
    @Transactional
    public String deleteDocument(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Document document = findDocumentForUpdate(roomId, documentId, username);

        if (document.isPrivateAt(Instant.now())) {
            throw new MatchConflictException("Match files can't be deleted until the match ends");
        }
        if (documentRepository.countByRoomId(roomId) <= 1) {
            throw new LastFileException("A room needs at least one file; add another before deleting this one");
        }

        String name = document.getFileName();
        int operations = documentOperationRepository.deleteByDocumentId(documentId);
        documentSnapshotRepository.deleteByDocumentId(documentId);
        documentRepository.deleteNow(documentId);

        log.info("{} deleted {} ({} edits) in room {}", username, name, operations, roomId);
        return name;
    }

    private Room lockRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    private static String validName(String fileName) {
        String name = fileName == null ? null : fileName.strip();
        String problem = FileTemplates.problemWith(name);
        if (problem != null) {
            throw new InvalidFileNameException(problem);
        }
        return name;
    }

    private static DuplicateFileException duplicate(String name) {
        return new DuplicateFileException("A file named " + name + " already exists in this room");
    }

    private static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContent(),
                document.getUpdatedAt(),
                document.getRevision(),
                document.getOwnerUsername(),
                document.getPrivateUntil(),
                document.getProblem() == null ? null : document.getProblem().getSlug()
        );
    }

    private static String privateTo(Document document) {
        return document.isPrivateAt(Instant.now()) ? document.getOwnerUsername() : null;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(UUID roomId, String username) {

        roomService.checkRoomAccess(roomId, username);

        Instant now = Instant.now();
        // Other players' match files are left out entirely, not just emptied
        return documentRepository.findByRoomIdOrderByFileNameAsc(roomId)
                .stream()
                .filter(doc -> doc.isVisibleTo(username, now))
                .map(DocumentService::toResponse)
                .toList();
    }

    @Transactional
    public AppliedOperation applyOperation(
            UUID roomId,
            UUID documentId,
            long baseRevision,
            TextOperation operation,
            String username
    ) {
        Document document = lockDocument(roomId, documentId, username);
        return applyLocked(document, baseRevision, operation, username);
    }

    /**
     * Brings back the text of an earlier revision. It is applied as one ordinary edit on top
     * of the current revision, so everyone in the room receives it live and the restore itself
     * can be undone from the history. If the text is already the same, nothing is saved and the
     * returned operation is a no-op at the current revision.
     */
    @Transactional
    public AppliedOperation restoreRevision(UUID roomId, UUID documentId, Long revision, String username) {
        if (revision == null) {
            throw new InvalidRevisionException("revision is required");
        }

        Document document = lockDocument(roomId, documentId, username);
        String target = historyService.contentAt(document, revision);
        TextOperation change = TextOperation.between(document.getContent(), target);

        if (change.isNoop()) {
            return new AppliedOperation(document.getId(), document.getRevision(), change, privateTo(document));
        }

        log.info("{} restored {} to revision {}", username, document.getFileName(), revision);
        return applyLocked(document, document.getRevision(), change, username);
    }

    private Document lockDocument(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);
        return findDocumentForUpdate(roomId, documentId, username);
    }

    // Someone else's private file answers exactly like a missing one, so its existence stays hidden
    private Document findDocumentForUpdate(UUID roomId, UUID documentId, String username) {
        return documentRepository
                .findByIdAndRoomIdForUpdate(documentId, roomId)
                .filter(doc -> doc.isVisibleTo(username, Instant.now()))
                .orElseThrow(() ->
                        new DocumentNotFoundException("Document not found in this room")
                );
    }

    private AppliedOperation applyLocked(
            Document document,
            long baseRevision,
            TextOperation operation,
            String username
    ) {
        UUID documentId = document.getId();
        long currentRevision = document.getRevision();

        if (baseRevision < 0 || baseRevision > currentRevision) {
            throw new IllegalArgumentException(
                    "Invalid baseRevision " + baseRevision + " (document is at " + currentRevision + ")");
        }

        List<TextOperation> missed = documentOperationRepository
                .findByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(documentId, baseRevision)
                .stream()
                .map(entry -> TextOperation.fromJsonString(entry.getOperation()))
                .toList();

        // Revisions saved before the operation log existed have no entries, so a client
        // that far behind cannot be rebased and has to reload the document.
        if (missed.size() != currentRevision - baseRevision) {
            throw new ResyncRequiredException(
                    "Cannot rebase from revision " + baseRevision + "; reload the document");
        }

        TextOperation rebased = TextOperation.rebase(operation, missed);
        String newContent = rebased.apply(document.getContent());

        if (newContent.length() > MAX_DOCUMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Document would exceed " + MAX_DOCUMENT_LENGTH + " characters");
        }

        if (!TextOperation.isWellFormedUtf16(newContent)) {
            throw new IllegalArgumentException("Operation would split a character in half");
        }

        historyService.beforeEdit(document);
        document.setContent(newContent);

        documentOperationRepository.save(new DocumentOperation(
                document,
                document.getRevision(),
                rebased.toJsonString(),
                username
        ));
        historyService.afterEdit(document);

        if (!missed.isEmpty()) {
            log.info("Rebased edit from {} on {} past {} operation(s): revision {} -> {}",
                    username, document.getFileName(), missed.size(), baseRevision, document.getRevision());
        }

        return new AppliedOperation(document.getId(), document.getRevision(), rebased, privateTo(document));
    }
}
