package com.codesync.backend.config;

import com.codesync.backend.security.JwtService;
import com.codesync.backend.service.RoomService;

import io.jsonwebtoken.JwtException;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public class WebSocketAuthInterceptor
        implements ChannelInterceptor {

    private final JwtService jwtService;
    private final RoomService roomService;

    public WebSocketAuthInterceptor(
            JwtService jwtService,
            RoomService roomService
    ) {
        this.jwtService = jwtService;
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        // Authenticate the STOMP connection
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authorization =
                    accessor.getFirstNativeHeader("Authorization");

            if (authorization == null
                    || !authorization.startsWith("Bearer ")) {

                throw new IllegalArgumentException(
                        "Missing WebSocket Authorization header"
                );
            }

            String token =
                    authorization.substring(7);

            try {

                String username =
                        jwtService.extractUsername(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                java.util.Collections.emptyList()
                        );

                accessor.setUser(authentication);

                System.out.println(
                        "WebSocket authenticated user: "
                                + username
                );

            } catch (JwtException | IllegalArgumentException exception) {

                throw new IllegalArgumentException(
                        "Invalid WebSocket token"
                );
            }
        }

        // Authorize room subscriptions
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

            String destination = accessor.getDestination();

            if (destination == null) {
                throw new IllegalArgumentException(
                        "Missing subscription destination"
                );
            }

            String prefix = "/topic/rooms/";
            String suffix = "/code";

            if (destination.startsWith(prefix)
                    && destination.endsWith(suffix)) {

                String roomId =
                        destination.substring(
                                prefix.length(),
                                destination.length() - suffix.length()
                        );

                if (accessor.getUser() == null) {
                    throw new IllegalArgumentException(
                            "WebSocket user is not authenticated"
                    );
                }

                String username =
                        accessor.getUser().getName();

                try {
    roomService.checkRoomAccess(
            java.util.UUID.fromString(roomId),
            username
    );

    System.out.println(
            "WebSocket subscription authorized: "
                    + username
                    + " -> room "
                    + roomId
    );

} catch (RuntimeException exception) {

    System.out.println(
            "WebSocket subscription REJECTED: "
                    + username
                    + " -> room "
                    + roomId
                    + " | "
                    + exception.getMessage()
    );

    throw exception;
}
            }
        }

        return message;
    }
}