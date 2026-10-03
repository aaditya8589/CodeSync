package com.codesync.backend.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rooms/{roomId}/test")
    public void testRoomMessage(
            @DestinationVariable String roomId,
            String message
    ) {
        String destination = "/topic/rooms/" + roomId;

        messagingTemplate.convertAndSend(
                destination,
                message
        );
    }
}