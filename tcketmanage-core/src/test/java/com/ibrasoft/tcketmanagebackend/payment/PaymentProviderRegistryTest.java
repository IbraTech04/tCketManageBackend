package com.ibrasoft.tcketmanagebackend.payment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentProviderRegistryTest {

    private PaymentProvider providerWithId(String id) {
        PaymentProvider p = mock(PaymentProvider.class);
        when(p.id()).thenReturn(id);
        return p;
    }

    private PaymentProperties propsWithDefault(String def) {
        PaymentProperties props = new PaymentProperties();
        props.setDefaultProvider(def);
        return props;
    }

    @Test
    void resolve_blankUsesDefault() {
        PaymentProvider mock = providerWithId("mock");
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(mock), propsWithDefault("mock"));

        assertSame(mock, registry.resolve(null));
        assertSame(mock, registry.resolve(""));
    }

    @Test
    void resolve_byId() {
        PaymentProvider mock = providerWithId("mock");
        PaymentProvider interac = providerWithId("interac");
        PaymentProviderRegistry registry =
                new PaymentProviderRegistry(List.of(mock, interac), propsWithDefault("mock"));

        assertSame(interac, registry.resolve("interac"));
    }

    @Test
    void resolve_unknownThrows() {
        PaymentProvider mock = providerWithId("mock");
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(mock), propsWithDefault("mock"));

        assertThrows(IllegalArgumentException.class, () -> registry.resolve("stripe"));
    }
}
