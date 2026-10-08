package com.codesync.backend.service;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.DocumentOperation;
import com.codesync.backend.exception.DocumentNotFoundException;
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

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentOperationRepository documentOperationRepository,
            RoomService roomService
    ) {
        this.documentRepository = documentRepository;
        this.documentOperationRepository = documentOperationRepository;
        this.roomService = roomService;
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
        roomService.checkRoomAccess(roomId, username);

        Document document = documentRepository
                .findByIdAndRoomIdForUpdate(documentId, roomId)
                .orElseThrow(() ->
                        new DocumentNotFoundException("Document not found in this room")
                );

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

        document.setContent(newContent);

        documentOperationRepository.save(new DocumentOperation(
                document,
                document.getRevision(),
                rebased.toJsonString(),
                username
        ));

        if (!missed.isEmpty()) {
            log.info("Rebased edit from {} on {} past {} operation(s): revision {} -> {}",
                    username, document.getFileName(), missed.size(), baseRevision, document.getRevision());
        }

        return new AppliedOperation(document.getId(), document.getRevision(), rebased);
    }
}
