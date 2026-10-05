package com.codesync.backend.service;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

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
                        doc.getUpdatedAt()
                ))
                .toList();
    }
}