package com.codesync.backend.controller;

import com.codesync.backend.dto.RunRequest;
import com.codesync.backend.execution.ExecutionResult;
import com.codesync.backend.execution.ExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/api/rooms/{roomId}/documents/{documentId}/run")
    public ResponseEntity<ExecutionResult> run(
            @PathVariable UUID roomId,
            @PathVariable UUID documentId,
            @RequestBody(required = false) RunRequest request,
            Authentication authentication
    ) {
        String stdin = request == null ? null : request.stdin();
        return ResponseEntity.ok(
                executionService.run(roomId, documentId, stdin, authentication.getName())
        );
    }
}
