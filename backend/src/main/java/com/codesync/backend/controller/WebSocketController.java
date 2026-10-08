package com.codesync.backend.controller;

import com.codesync.backend.dto.AppliedOperationMessage;
import com.codesync.backend.dto.OperationErrorMessage;
import com.codesync.backend.dto.OperationMessage;
import com.codesync.backend.exception.ResyncRequiredException;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.service.DocumentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private static final int MAX_CLIENT_ID_LENGTH = 64;

    private final SimpMessagingTemplate messagingTemplate;
    private final DocumentService documentService;

    public WebSocketController(
            SimpMessagingTemplate messagingTemplate,
            DocumentService documentService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.documentService = documentService;
    }

    @MessageMapping("/rooms/{roomId}/code")
    public void handleOperation(
            @DestinationVariable String roomId,
            OperationMessage message,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("WebSocket user is not authenticated");
        }

        String username = principal.getName();

        try {
            validate(message);

            DocumentService.AppliedOperation applied = documentService.applyOperation(
                    UUID.fromString(roomId),
                    UUID.fromString(message.documentId()),
                    message.baseRevision(),
                    TextOperation.fromJson(message.operation()),
                    username
            );

            messagingTemplate.convertAndSend(
                    "/topic/rooms/" + roomId + "/code",
                    new AppliedOperationMessage(
                            applied.documentId().toString(),
                            applied.revision(),
                            applied.operation().toJson(),
                            message.clientId(),
                            username
                    )
            );

        } catch (RuntimeException exception) {
            // The sender is waiting for an acknowledgement, so it must hear about a rejection.
            String code = exception instanceof ResyncRequiredException ? "RESYNC" : "REJECTED";

            log.warn("Operation {} from {} in room {}: {}", code, username, roomId, exception.getMessage());

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/errors",
                    new OperationErrorMessage(
                            message == null ? null : message.clientId(),
                            message == null ? null : message.documentId(),
                            code,
                            exception.getMessage()
                    )
            );
        }
    }

    private void validate(OperationMessage message) {
        if (message == null
                || message.documentId() == null
                || message.baseRevision() == null
                || message.operation() == null
                || message.clientId() == null) {
            throw new IllegalArgumentException(
                    "Operation needs documentId, baseRevision, operation and clientId");
        }

        if (message.clientId().isEmpty() || message.clientId().length() > MAX_CLIENT_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid clientId");
        }
    }
}
