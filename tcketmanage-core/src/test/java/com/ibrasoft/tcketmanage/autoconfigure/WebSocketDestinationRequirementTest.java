package com.ibrasoft.tcketmanage.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketDestinationRequirementTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<AbstractBrokerMessageHandler> brokers(Collection<String>... prefixesPerBroker) {
        List<AbstractBrokerMessageHandler> handlers = Stream.of(prefixesPerBroker).map(prefixes -> {
            AbstractBrokerMessageHandler handler = mock(AbstractBrokerMessageHandler.class);
            when(handler.getDestinationPrefixes()).thenReturn(prefixes);
            return handler;
        }).toList();

        ObjectProvider<AbstractBrokerMessageHandler> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(invocation -> handlers.stream());
        return provider;
    }

    private TcketManageProperties propertiesWithPrefix(String prefix) {
        TcketManageProperties properties = new TcketManageProperties();
        properties.getWebsocket().setTopicPrefix(prefix);
        return properties;
    }

    @Test
    void acceptsBrokerServingTheConfiguredPrefix() {
        WebSocketDestinationRequirement requirement =
                new WebSocketDestinationRequirement(brokers(List.of("/topic")), propertiesWithPrefix("/topic"));

        assertDoesNotThrow(requirement::afterSingletonsInstantiated);
    }

    /** A broker with no declared prefixes serves every destination. */
    @Test
    void acceptsBrokerWithoutDestinationPrefixes() {
        WebSocketDestinationRequirement requirement =
                new WebSocketDestinationRequirement(brokers(List.of()), propertiesWithPrefix("/topic"));

        assertDoesNotThrow(requirement::afterSingletonsInstantiated);
    }

    /** The failure this guard exists for: the broker would drop core's messages without a word. */
    @Test
    void rejectsPrefixNoBrokerServes() {
        WebSocketDestinationRequirement requirement =
                new WebSocketDestinationRequirement(brokers(List.of("/queue")), propertiesWithPrefix("/topic"));

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, requirement::afterSingletonsInstantiated);
        assertTrue(thrown.getMessage().contains("/topic/email-jobs/{jobId}"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("/queue"), thrown.getMessage());
    }

    @Test
    void acceptsWhenAnyOfSeveralBrokersServesThePrefix() {
        WebSocketDestinationRequirement requirement = new WebSocketDestinationRequirement(
                brokers(List.of("/queue"), List.of("/topic")), propertiesWithPrefix("/topic"));

        assertDoesNotThrow(requirement::afterSingletonsInstantiated);
    }

    /** Messaging switched off entirely is a different problem; this guard has nothing to check. */
    @Test
    void skipsWhenNoBrokerIsPresent() {
        WebSocketDestinationRequirement requirement =
                new WebSocketDestinationRequirement(brokers(), propertiesWithPrefix("/topic"));

        assertDoesNotThrow(requirement::afterSingletonsInstantiated);
    }

    /** A trailing slash in configuration must not turn into a "//" in the destination. */
    @Test
    void normalizesTrailingSlashesInThePrefix() {
        TcketManageProperties properties = propertiesWithPrefix("/topic/");

        assertDoesNotThrow(new WebSocketDestinationRequirement(
                brokers(List.of("/topic")), properties)::afterSingletonsInstantiated);
        assertTrue(properties.getWebsocket().emailJobDestination("abc").equals("/topic/email-jobs/abc"));
    }
}
