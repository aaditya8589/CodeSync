package com.codesync.backend.history;

import com.codesync.backend.dto.HistoryResponse;
import com.codesync.backend.dto.RevisionResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.DocumentOperation;
import com.codesync.backend.entity.DocumentSnapshot;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.InvalidRevisionException;
import com.codesync.backend.exception.RevisionNotAvailableException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.repository.DocumentOperationRepository;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.DocumentSnapshotRepository;
import com.codesync.backend.service.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentHistoryService {

    // Rebuilding any revision replays at most this many operations after a snapshot
    public static final int SNAPSHOT_INTERVAL = 100;

    // The list is for people to click through; older versions stay reachable by revision
    public static final int MAX_VERSIONS = 200;

    private final DocumentRepository documentRepository;
    private final DocumentOperationRepository operationRepository;
    private final DocumentSnapshotRepository snapshotRepository;
    private final RoomService roomService;

    public DocumentHistoryService(
            DocumentRepository documentRepository,
            DocumentOperationRepository operationRepository,
            DocumentSnapshotRepository snapshotRepository,
            RoomService roomService
    ) {
        this.documentRepository = documentRepository;
        this.operationRepository = operationRepository;
        this.snapshotRepository = snapshotRepository;
        this.roomService = roomService;
    }

    /** Call with the document locked, before changing it. Starts the history at the first edit. */
    public void beforeEdit(Document document) {
        if (document.getHistoryStartRevision() == null) {
            snapshotRepository.save(new DocumentSnapshot(document, document.getRevision(), document.getContent()));
            document.setHistoryStartRevision(document.getRevision());
        }
    }

    /** Call with the document locked, after changing it. */
    public void afterEdit(Document document) {
        if (document.getRevision() % SNAPSHOT_INTERVAL == 0) {
            snapshotRepository.save(new DocumentSnapshot(document, document.getRevision(), document.getContent()));
        }
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistory(UUID roomId, UUID documentId, String username) {
        Document document = findDocument(roomId, documentId, username);
        Long start = document.getHistoryStartRevision();

        List<VersionGrouping.Version> versions = new ArrayList<>();
        if (start != null) {
            List<VersionGrouping.Edit> edits = operationRepository
                    .findSummariesByDocumentIdAndRevisionGreaterThanOrderByRevisionAsc(documentId, start)
                    .stream()
                    .map(edit -> new VersionGrouping.Edit(edit.getRevision(), edit.getAuthor(), edit.getCreatedAt()))
                    .toList();
            versions.addAll(VersionGrouping.group(start, edits));
            Collections.reverse(versions);
        }

        return new HistoryResponse(
                document.getId().toString(),
                document.getFileName(),
                document.getRevision(),
                start,
                versions.subList(0, Math.min(versions.size(), MAX_VERSIONS))
        );
    }

    @Transactional(readOnly = true)
    public RevisionResponse getRevision(UUID roomId, UUID documentId, long revision, String username) {
        Document document = findDocument(roomId, documentId, username);
        return new RevisionResponse(revision, contentAt(document, revision));
    }

    /** The document's text at an earlier revision. Must be called inside a transaction. */
    public String contentAt(Document document, long revision) {
        if (revision < 0 || revision > document.getRevision()) {
            throw new InvalidRevisionException(
                    "Revision " + revision + " does not exist (document is at " + document.getRevision() + ")");
        }
        if (revision == document.getRevision()) {
            return document.getContent();
        }

        Long start = document.getHistoryStartRevision();
        if (start == null || revision < start) {
            throw new RevisionNotAvailableException("Revision " + revision + " is older than the saved history");
        }

        DocumentSnapshot snapshot = snapshotRepository
                .findFirstByDocumentIdAndRevisionLessThanEqualOrderByRevisionDesc(document.getId(), revision)
                .orElseThrow(() -> new RevisionNotAvailableException("No snapshot before revision " + revision));

        List<DocumentOperation> operations = operationRepository
                .findByDocumentIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevisionAsc(
                        document.getId(), snapshot.getRevision(), revision);

        if (operations.size() != revision - snapshot.getRevision()) {
            throw new RevisionNotAvailableException("Operations missing between snapshot and revision " + revision);
        }

        String content = snapshot.getContent();
        for (DocumentOperation operation : operations) {
            content = TextOperation.fromJsonString(operation.getOperation()).apply(content);
        }
        return content;
    }

    private Document findDocument(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);
        return documentRepository.findByIdAndRoomId(documentId, roomId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found in this room"));
    }
}
