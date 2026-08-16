package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;

import java.util.Collection;
import java.util.List;

/**
 * Fail-fast guard on the destination core publishes email-job progress to.
 *
 * <p>Core stopped configuring the message broker so that a host embedding it keeps ownership of its
 * own prefixes (see {@link com.ibrasoft.tcketmanagebackend.config.WebSocketConfig}). That leaves a
 * gap: core still has to name a destination, and if the broker in force does not serve that prefix,
 * {@code SimpleBrokerMessageHandler} discards the message on a prefix mismatch — quietly, with no
 * exception anywhere. {@code POST <base-path>/events/{id}/tickets/resend} would still answer 202
 * while the operator UI hangs on progress that never arrives.
 *
 * <p>So the pairing is checked once at startup instead: if every broker in the context declares
 * destination prefixes and none of them covers {@code tcketmanage.websocket.topic-prefix}, the
 * context fails with an explanation rather than deploying a silently broken feature. A broker that
 * declares no prefixes accepts every destination, so it is always compatible.
 *
 * <p>Implemented as a {@link SmartInitializingSingleton} rather than checked in a constructor: the
 * broker handler is built from every {@code WebSocketMessageBrokerConfigurer} in the context, so it
 * must not be pulled early in another bean's initialisation.
 */
class WebSocketDestinationRequirement implements SmartInitializingSingleton {

    private final ObjectProvider<AbstractBrokerMessageHandler> brokers;
    private final TcketManageProperties properties;

    WebSocketDestinationRequirement(ObjectProvider<AbstractBrokerMessageHandler> brokers,
                                    TcketManageProperties properties) {
        this.brokers = brokers;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String prefix = properties.getWebsocket().normalizedTopicPrefix();
        List<AbstractBrokerMessageHandler> present = brokers.stream().toList();
        if (present.isEmpty()) {
            // No broker at all: STOMP messaging is off entirely, which is a separate concern from
            // whether core's prefix is right. Nothing to verify.
            return;
        }
        boolean served = present.stream().anyMatch(broker -> serves(broker, prefix));
        if (!served) {
            throw new IllegalStateException(
                    "tCketManage publishes email-job progress to \"" + prefix + "/email-jobs/{jobId}\", "
                    + "but no STOMP broker in this application serves that prefix (configured prefixes: "
                    + declaredPrefixes(present) + "). The broker would drop those messages silently, so "
                    + "bulk-email progress would never reach a subscriber. Set "
                    + "tcketmanage.websocket.topic-prefix to a prefix your broker serves, or add \""
                    + prefix + "\" to it.");
        }
    }

    private boolean serves(AbstractBrokerMessageHandler broker, String prefix) {
        Collection<String> prefixes = broker.getDestinationPrefixes();
        // An empty prefix list means the broker accepts any destination.
        return prefixes == null || prefixes.isEmpty()
                || prefixes.stream().anyMatch(declared -> (prefix + "/").startsWith(declared));
    }

    private String declaredPrefixes(List<AbstractBrokerMessageHandler> present) {
        return present.stream()
                .map(AbstractBrokerMessageHandler::getDestinationPrefixes)
                .map(prefixes -> prefixes == null || prefixes.isEmpty() ? "<any>" : prefixes.toString())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }
}
