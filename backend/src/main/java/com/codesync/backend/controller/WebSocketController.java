package com.codesync.backend.controller;

import com.codesync.backend.dto.CodeChangeMessage;
import com.codesync.backend.service.RoomService;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    public WebSocketController(
            SimpMessagingTemplate messagingTemplate,
            RoomService roomService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.roomService = roomService;
    }

    @MessageMapping("/rooms/{roomId}/code")
    public void handleCodeChange(
            @DestinationVariable String roomId,
            CodeChangeMessage message,
            Principal principal
    ) {

        if (principal == null) {
    throw new IllegalStateException(
            "WebSocket user is not authenticated"
    );
}

String username = principal.getName();

        // Verify that the authenticated user belongs to this room.
        roomService.checkRoomAccess(
                java.util.UUID.fromString(roomId),
                username
        );

        // Make sure the room in the message matches
        // the room in the STOMP destination.
        if (!roomId.equals(message.getRoomId())) {
            throw new IllegalArgumentException(
                    "Room ID in message does not match destination"
            );
        }

        String destination =
                "/topic/rooms/" + roomId + "/code";

        messagingTemplate.convertAndSend(
                destination,
                message
        );
    }
}