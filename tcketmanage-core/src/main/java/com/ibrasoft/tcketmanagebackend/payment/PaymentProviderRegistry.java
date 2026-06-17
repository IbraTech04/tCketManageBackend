package com.ibrasoft.tcketmanagebackend.payment;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the active {@link PaymentProvider} by id. Only enabled providers are Spring beans
 * (each is {@code @ConditionalOnProperty}), so this map reflects exactly what the deployment wired in.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;
    private final PaymentProperties properties;

    public PaymentProviderRegistry(List<PaymentProvider> providerBeans, PaymentProperties properties) {
        this.providers = providerBeans.stream()
                .collect(Collectors.toMap(PaymentProvider::id, Function.identity()));
        this.properties = properties;
    }

    /**
     * Returns the provider for the given id, or the configured default when {@code providerId} is
     * blank. Throws {@link IllegalArgumentException} if no such provider is enabled.
     */
    public PaymentProvider resolve(String providerId) {
        String id = (providerId == null || providerId.isBlank())
                ? properties.getDefaultProvider()
                : providerId;
        PaymentProvider provider = providers.get(id);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown or disabled payment provider: " + id);
        }
        return provider;
    }
}
