package com.codesync.backend.controller;

import com.codesync.backend.dto.CreateDocumentRequest;
import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.dto.FileAddedMessage;
import com.codesync.backend.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    public DocumentController(DocumentService documentService, SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @PathVariable UUID roomId,
            @RequestBody CreateDocumentRequest request,
            Authentication authentication
    ) {
        DocumentResponse created = documentService.createDocument(
                roomId, request.fileName(), authentication.getName());

        // After the commit, so a tab that reloads on this message sees the new file
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/files",
                new FileAddedMessage(created.getId().toString(), created.getFileName(), authentication.getName())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @PathVariable UUID roomId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                documentService.getDocuments(roomId, authentication.getName())
        );
    }
}