package com.codesync.backend.controller;

import com.codesync.backend.dto.AppliedOperationMessage;
import com.codesync.backend.dto.FileChangeMessage;
import com.codesync.backend.service.DocumentService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends edits and file changes to the room, except those of a private match file: they go
 * only to the owner's own connections (/user/queue/rooms/{id}/...), so another player's
 * browser never receives a single character of it.
 */
@Component
public class RoomBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public RoomBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void operation(UUID roomId, DocumentService.AppliedOperation applied, String clientId, String author) {
        AppliedOperationMessage message = new AppliedOperationMessage(
                applied.documentId().toString(),
                applied.revision(),
                applied.operation().toJson(),
                clientId,
                author
        );
        send(roomId, "code", message, applied.privateTo());
    }

    public void fileChange(UUID roomId, FileChangeMessage message, String privateTo) {
        send(roomId, "files", message, privateTo);
    }

    private void send(UUID roomId, String channel, Object message, String privateTo) {
        if (privateTo == null) {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/" + channel, message);
        } else {
            messagingTemplate.convertAndSendToUser(privateTo, "/queue/rooms/" + roomId + "/" + channel, message);
        }
    }
}
