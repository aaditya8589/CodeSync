package com.codesync.backend.controller;

import com.codesync.backend.dto.CodeChangeMessage;
import com.codesync.backend.entity.Document;
import com.codesync.backend.service.DocumentService;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class WebSocketController {

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
    public void handleCodeChange(
            @DestinationVariable String roomId,
            CodeChangeMessage message,
            Principal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException("WebSocket user is not authenticated");
        }

        // The destination is the source of truth for the room
        if (!roomId.equals(message.getRoomId())) {
            throw new IllegalArgumentException("Room ID in message does not match destination");
        }

        if (message.getDocumentId() == null) {
            throw new IllegalArgumentException("Code change is missing documentId");
        }

        if (message.getBaseRevision() == null) {
            throw new IllegalArgumentException("Code change is missing baseRevision");
        }

        // Checks membership, checks the document belongs to the room, saves it
        Document document = documentService.updateContent(
                UUID.fromString(roomId),
                UUID.fromString(message.getDocumentId()),
                message.getContent(),
                message.getBaseRevision(),
                principal.getName()
        );

        // Broadcast the server's file name and new revision
        CodeChangeMessage broadcast = new CodeChangeMessage(
                roomId,
                document.getId().toString(),
                document.getFileName(),
                document.getContent(),
                document.getRevision()
        );

        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/code", broadcast);
    }
}