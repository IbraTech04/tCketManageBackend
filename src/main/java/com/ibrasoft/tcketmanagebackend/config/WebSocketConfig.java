package com.ibrasoft.tcketmanagebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket setup for pushing live bulk-email delivery progress to operators.
 *
 * <p>Clients connect to {@code /ws} (SockJS fallback enabled for browsers behind proxies that don't
 * speak raw WebSocket) and subscribe to {@code /topic/email-jobs/{jobId}}. The server only pushes —
 * there are no client-to-server message mappings — so an in-memory simple broker is sufficient.
 * Origins mirror the REST CORS allow-list ({@code web.cors.allowed-origins}).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${web.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Origin *patterns* (not allowedOrigins) so a "*" wildcard is permitted alongside SockJS.
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
