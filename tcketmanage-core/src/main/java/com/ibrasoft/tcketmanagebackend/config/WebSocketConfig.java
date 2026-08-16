package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanage.autoconfigure.WebSocketBrokerFallbackConfig;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@AllArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TcketManageProperties properties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getBasePath() + "/ws")
                .setAllowedOriginPatterns(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .withSockJS();
    }
}
