package com.codesync.backend.service;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.exception.DocumentNotFoundException;
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

    private final DocumentRepository documentRepository;
    private final RoomService roomService;

    public DocumentService(DocumentRepository documentRepository, RoomService roomService) {
        this.documentRepository = documentRepository;
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
    public Document updateContent(
            UUID roomId,
            UUID documentId,
            String content,
            long baseRevision,
            String username
    ) {
        roomService.checkRoomAccess(roomId, username);

        Document document = documentRepository
                .findByIdAndRoomId(documentId, roomId)
                .orElseThrow(() ->
                        new DocumentNotFoundException("Document not found in this room")
                );

        long currentRevision = document.getRevision();

                if (baseRevision != currentRevision) {
            // Detect only, for now: we still save, so we can measure how often this happens
            log.warn(
                    "STALE EDIT: user={} file={} baseRevision={} serverRevision={} (behind by {})",
                    username,
                    document.getFileName(),
                    baseRevision,
                    currentRevision,
                    currentRevision - baseRevision
            );
        }

        document.setContent(content);

        return document;
    }
}