package com.codesync.backend.config;

import com.codesync.backend.presence.PresenceRegistry;
import com.codesync.backend.security.JwtService;
import com.codesync.backend.service.RoomService;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final RoomService roomService;
    private final PresenceRegistry presenceRegistry;

    public WebSocketConfig(
            JwtService jwtService,
            RoomService roomService,
            PresenceRegistry presenceRegistry
    ) {
        this.jwtService = jwtService;
        this.roomService = roomService;
        this.presenceRegistry = presenceRegistry;
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");

        // Operations must reach each client in revision order. Without this, Spring may
        // deliver messages to the same client in parallel and therefore out of order.
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173");
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                new WebSocketAuthInterceptor(
                        jwtService,
                        roomService,
                        presenceRegistry
                )
        );
    }
}