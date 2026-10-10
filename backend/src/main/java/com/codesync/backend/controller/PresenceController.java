package com.codesync.backend.controller;

import com.codesync.backend.dto.CursorMessage;
import com.codesync.backend.dto.PresenceJoinMessage;
import com.codesync.backend.dto.PresenceMessage;
import com.codesync.backend.dto.RemoteCursorMessage;
import com.codesync.backend.presence.PresenceRegistry;
import com.codesync.backend.service.DocumentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.UUID;

@Controller
public class PresenceController {

    private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceRegistry registry;

    public PresenceController(SimpMessagingTemplate messagingTemplate, PresenceRegistry registry) {
        this.messagingTemplate = messagingTemplate;
        this.registry = registry;
    }

    @MessageMapping("/rooms/{roomId}/presence")
    public void join(
            @DestinationVariable String roomId,
            PresenceJoinMessage message,
            SimpMessageHeaderAccessor accessor
    ) {
        UUID room = parseRoom(roomId);
        String sessionId = accessor.getSessionId();

        if (room == null || message == null || !registry.join(sessionId, room, message.clientId())) {
            log.warn("Presence join REJECTED: session {} -> room {}", sessionId, roomId);
            return;
        }

        broadcastPresence(room);
    }

    @MessageMapping("/rooms/{roomId}/cursor")
    public void cursor(
            @DestinationVariable String roomId,
            CursorMessage message,
            SimpMessageHeaderAccessor accessor
    ) {
        UUID room = parseRoom(roomId);
        String sessionId = accessor.getSessionId();

        // Cursors move constantly, so they are checked against the in-memory registry
        // (filled when the subscription passed the membership check), not the database.
        if (room == null || !registry.isAuthorized(sessionId, room)) {
            return;
        }

        String clientId = registry.clientIdOf(sessionId, room);
        if (clientId == null || !isValid(message)) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room + "/cursors",
                new RemoteCursorMessage(
                        clientId,
                        registry.usernameOf(sessionId),
                        message.documentId(),
                        message.revision(),
                        message.anchor(),
                        message.head()
                )
        );
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        for (UUID room : registry.remove(event.getSessionId())) {
            broadcastPresence(room);
        }
    }

    private void broadcastPresence(UUID room) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room + "/presence",
                new PresenceMessage(registry.members(room))
        );
    }

    private static boolean isValid(CursorMessage message) {
        if (message == null || message.documentId() == null || message.revision() == null
                || message.anchor() == null || message.head() == null) {
            return false;
        }
        if (message.revision() < 0
                || message.anchor() < 0 || message.anchor() > DocumentService.MAX_DOCUMENT_LENGTH
                || message.head() < 0 || message.head() > DocumentService.MAX_DOCUMENT_LENGTH) {
            return false;
        }
        try {
            UUID.fromString(message.documentId());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static UUID parseRoom(String roomId) {
        try {
            return UUID.fromString(roomId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
