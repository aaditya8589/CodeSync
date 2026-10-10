package com.codesync.backend.controller;

import com.codesync.backend.dto.AppliedOperationMessage;
import com.codesync.backend.dto.HistoryResponse;
import com.codesync.backend.dto.RestoreRequest;
import com.codesync.backend.dto.RestoreResponse;
import com.codesync.backend.dto.RevisionResponse;
import com.codesync.backend.history.DocumentHistoryService;
import com.codesync.backend.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/documents/{documentId}")
public class DocumentHistoryController {

    // Not a real tab, so every open editor treats a restore as someone else's edit
    static final String RESTORE_CLIENT_ID = "server-restore";

    private final DocumentHistoryService historyService;
    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    public DocumentHistoryController(
            DocumentHistoryService historyService,
            DocumentService documentService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.historyService = historyService;
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> history(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(historyService.getHistory(roomId, documentId, authentication.getName()));
    }

    @GetMapping("/revisions/{revision}")
    public ResponseEntity<RevisionResponse> revision(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            @PathVariable long revision,
            Authentication authentication
    ) {
        return ResponseEntity.ok(historyService.getRevision(roomId, documentId, revision, authentication.getName()));
    }

    @PostMapping("/restore")
    public ResponseEntity<RestoreResponse> restore(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            @RequestBody RestoreRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        DocumentService.AppliedOperation applied = documentService.restoreRevision(
                roomId, documentId, request.revision(), username);

        if (applied.operation().isNoop()) {
            return ResponseEntity.ok(new RestoreResponse(applied.revision(), false));
        }

        // Sent after the transaction has committed, exactly like a typed edit
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/code",
                new AppliedOperationMessage(
                        applied.documentId().toString(),
                        applied.revision(),
                        applied.operation().toJson(),
                        RESTORE_CLIENT_ID,
                        username
                )
        );
        return ResponseEntity.ok(new RestoreResponse(applied.revision(), true));
    }
}
