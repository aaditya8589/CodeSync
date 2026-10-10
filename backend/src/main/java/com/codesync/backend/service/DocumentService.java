package com.codesync.backend.service;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.DocumentOperation;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.InvalidRevisionException;
import com.codesync.backend.history.DocumentHistoryService;
import com.codesync.backend.exception.ResyncRequiredException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentOperationRepository;
import com.codesync.backend.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    public static final int MAX_DOCUMENT_LENGTH = 500_000;

    public record AppliedOperation(UUID documentId, long revision, TextOperation operation) {
    }

    private final DocumentRepository documentRepository;
    private final DocumentOperationRepository documentOperationRepository;
    private final RoomService roomService;
    private final DocumentHistoryService historyService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentOperationRepository documentOperationRepository,
            RoomService roomService,
            DocumentHistoryService historyService
    ) {
        this.documentRepository = documentRepository;
        this.documentOperationRepository = documentOperationRepository;
        this.roomService = roomService;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(UUID roomId, String username) {

        roomService.checkRoomAccess(roomId, username);

        return documentRepository.findByRoomIdOrderByFileNameAsc(roomId)
                .stream()
                .map(doc -> new DocumentResponse(
                        doc.getId(),
                        doc.getFileName(),
                        doc.getContent(),
                        doc.getUpdatedAt(),
                        doc.getRevision()
                ))
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
            return new AppliedOperation(document.getId(), document.getRevision(), change);
        }

        log.info("{} restored {} to revision {}", username, document.getFileName(), revision);
        return applyLocked(document, document.getRevision(), change, username);
    }

    private Document lockDocument(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);

        return documentRepository
                .findByIdAndRoomIdForUpdate(documentId, roomId)
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

        return new AppliedOperation(document.getId(), document.getRevision(), rebased);
    }
}
