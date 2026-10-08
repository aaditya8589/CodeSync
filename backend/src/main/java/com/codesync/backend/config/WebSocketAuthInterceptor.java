package com.codesync.backend.config;

import com.codesync.backend.security.JwtService;
import com.codesync.backend.service.RoomService;

import io.jsonwebtoken.JwtException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    // The only destination a client may subscribe to. Anything else, including
    // wildcard patterns like /topic/rooms/**, is rejected.
    private static final Pattern ROOM_TOPIC =
            Pattern.compile("^/topic/rooms/([0-9a-fA-F-]{36})/code$");

    // Each user's private channel for rejected operations. Spring resolves it to the
    // subscriber's own sessions, so no room check is needed.
    private static final String USER_ERRORS = "/user/queue/errors";

    // Clients may only send to application handlers. Sending to /topic would
    // go straight to the broker and skip all server-side checks.
    private static final String APPLICATION_PREFIX = "/app/";

    private final JwtService jwtService;
    private final RoomService roomService;

    public WebSocketAuthInterceptor(JwtService jwtService, RoomService roomService) {
        this.jwtService = jwtService;
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing WebSocket Authorization header");
        }

        try {
            String username = jwtService.extractUsername(authorization.substring(7));

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    username, null, Collections.emptyList()));

            log.info("WebSocket authenticated user: {}", username);

        } catch (JwtException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String username = requireUser(accessor);
        String destination = accessor.getDestination();

        if (USER_ERRORS.equals(destination)) {
            return;
        }

        Matcher matcher = destination == null ? null : ROOM_TOPIC.matcher(destination);

        if (matcher == null || !matcher.matches()) {
            log.warn("WebSocket subscription REJECTED: {} -> {} | destination not allowed", username, destination);
            throw new IllegalArgumentException("Subscription destination not allowed");
        }

        String roomId = matcher.group(1);

        try {
            roomService.checkRoomAccess(UUID.fromString(roomId), username);
            log.info("WebSocket subscription authorized: {} -> room {}", username, roomId);

        } catch (RuntimeException exception) {
            log.warn("WebSocket subscription REJECTED: {} -> room {} | {}", username, roomId, exception.getMessage());
            throw exception;
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String username = requireUser(accessor);
        String destination = accessor.getDestination();

        if (destination == null || !destination.startsWith(APPLICATION_PREFIX)) {
            log.warn("WebSocket send REJECTED: {} -> {} | destination not allowed", username, destination);
            throw new IllegalArgumentException("Send destination not allowed");
        }
    }

    private String requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();

        if (user == null) {
            throw new IllegalArgumentException("WebSocket user is not authenticated");
        }

        return user.getName();
    }
}