package com.codesync.backend.controller;

import com.codesync.backend.dto.CreateDocumentRequest;
import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.dto.FileChangeMessage;
import com.codesync.backend.dto.RenameDocumentRequest;
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

        // Sent after the commit (the service method has returned), so a tab that reloads on
        // this message sees the change
        broadcast(roomId, new FileChangeMessage(FileChangeMessage.Type.CREATED,
                created.getId().toString(), created.getFileName(), null, authentication.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> renameDocument(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            @RequestBody RenameDocumentRequest request,
            Authentication authentication
    ) {
        DocumentService.RenamedDocument renamed = documentService.renameDocument(
                roomId, documentId, request.fileName(), authentication.getName());

        DocumentResponse document = renamed.document();
        if (!document.getFileName().equals(renamed.previousName())) {
            broadcast(roomId, new FileChangeMessage(FileChangeMessage.Type.RENAMED,
                    documentId.toString(), document.getFileName(), renamed.previousName(), authentication.getName()));
        }
        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        String name = documentService.deleteDocument(roomId, documentId, authentication.getName());
        broadcast(roomId, new FileChangeMessage(FileChangeMessage.Type.DELETED,
                documentId.toString(), null, name, authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    private void broadcast(UUID roomId, FileChangeMessage message) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/files", message);
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