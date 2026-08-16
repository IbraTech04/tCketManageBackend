package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Turns on STOMP messaging only if the application has not already done so.
 *
 * <p>Core needs a broker for its email-progress push, but must not be the one to switch messaging on
 * in a host that runs its own — {@code @EnableWebSocketMessageBroker} imports
 * {@link DelegatingWebSocketMessageBrokerConfiguration}, and the host's broker settings are its own
 * business.
 *
 * <p>The condition keys on that imported configuration class rather than on any core type, so it
 * detects the host's {@code @EnableWebSocketMessageBroker} however the host declared it. When the
 * host has one, this backs off entirely and core's
 * {@link com.ibrasoft.tcketmanagebackend.config.WebSocketConfig} simply adds its endpoint to the
 * host's broker. When nobody has — the standalone app — this supplies a plain in-memory broker with
 * core's historical prefixes.
 *
 * <p>The broker prefix is taken from {@code tcketmanage.websocket.topic-prefix} rather than being
 * hardcoded, so that the one case where core does own the broker cannot be configured into
 * disagreeing with itself: changing core's publish destination while this still enabled {@code /topic}
 * would mean the standalone app dropped every progress message.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(DelegatingWebSocketMessageBrokerConfiguration.class)
@EnableWebSocketMessageBroker
public class WebSocketBrokerFallbackConfig implements WebSocketMessageBrokerConfigurer {

    private final TcketManageProperties properties;

    public WebSocketBrokerFallbackConfig(TcketManageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(properties.getWebsocket().normalizedTopicPrefix());
        registry.setApplicationDestinationPrefixes("/app");
    }
}
