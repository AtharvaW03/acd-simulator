package com.sprint.acdsimulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 📚 LESSON — WebSocket + STOMP:
 *
 * HTTP is request-response: the client asks, the server answers, connection closes.
 * WebSocket is full-duplex: the connection stays open and either side can push messages at any time.
 *
 * STOMP (Simple Text Oriented Messaging Protocol) adds structure on top of raw WebSocket:
 *   - Topics:   server → many clients  (broadcast)  /topic/dashboard
 *   - Queues:   server → one client    (private)     /user/queue/...
 *   - Commands: client → server        @MessageMapping
 *
 * SockJS is a JavaScript library that falls back to HTTP long-polling if WebSocket is blocked
 * by a corporate firewall — important for real call centers.
 *
 * Flow:
 *   Browser opens WS connection to /ws-acd
 *   → Spring upgrades to STOMP session
 *   → Browser SUBSCRIBEs to /topic/dashboard
 *   → DashboardBroadcaster sends metrics every 2s → browser receives them
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable in-memory message broker for these destination prefixes
        config.enableSimpleBroker("/topic");
        // Prefix for messages FROM client TO server (@MessageMapping methods)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-acd")
                .setAllowedOriginPatterns("*")  // In production, restrict to your frontend domain
                .withSockJS();                  // SockJS fallback for browsers/firewalls
    }
}

