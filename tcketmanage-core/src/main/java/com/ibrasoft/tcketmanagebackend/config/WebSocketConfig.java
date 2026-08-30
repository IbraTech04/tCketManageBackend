package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanage.autoconfigure.WebSocketBrokerFallbackConfig;
import com.ibrasoft.tcketmanagebackend.security.AuthorizationGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Core's contribution to whichever STOMP broker is in force — its own endpoint, and its own inbound
 * channel interceptor. It does not enable messaging; see {@link WebSocketBrokerFallbackConfig} for
 * why that is left to the host wherever possible.
 *
 * <p>Both callbacks are additive by design. Spring collects every
 * {@link WebSocketMessageBrokerConfigurer} bean in the context and invokes each in turn, so in an
 * embedding host this configurer runs alongside the host's own: the endpoint is added to the host's
 * broker, and {@link ChannelRegistration#interceptors} appends to the interceptor list rather than
 * replacing it, so the host's interceptors (Spring Security's messaging support among them) are
 * untouched.
 */
@Configuration
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TcketManageProperties properties;
    private final EmailJobSubscriptionInterceptor emailJobSubscriptions;

    /**
     * @param authorization taken as a provider rather than as the bean: this configurer is pulled in
     *                      while the messaging infrastructure is being built, and core should not be
     *                      the reason the authorization gateway (and with it a host's
     *                      {@code TcketManageAuthorizer}) is instantiated at that point.
     */
    public WebSocketConfig(TcketManageProperties properties,
                           ObjectProvider<AuthorizationGateway> authorization) {
        this.properties = properties;
        this.emailJobSubscriptions =
                new EmailJobSubscriptionInterceptor(properties, authorization::getObject);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getBasePath() + "/ws")
                .setAllowedOriginPatterns(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    /**
     * Registers the guard on subscriptions to core's email-job feed. See
     * {@link EmailJobSubscriptionInterceptor} — subscriptions were previously unauthenticated and
     * could be wildcarded across every job in flight.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(emailJobSubscriptions);
    }
}
