package com.codesync.backend.controller;

import com.codesync.backend.dto.DocumentResponse;
import com.codesync.backend.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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