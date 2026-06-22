package com.ibrasoft.tcketmanagebackend.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Core's default reaction to {@link OrderQuarantinedEvent}: a loud log line once the quarantine has
 * committed, so the held seats and the pending operator decision are visible in the application logs.
 * Fires {@code AFTER_COMMIT} so we never announce a quarantine that later rolls back. An embedding
 * host that wants real alerting (email, Slack, dashboard) adds its own listener for the same event.
 */
@Component
public class OrderQuarantinedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderQuarantinedListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderQuarantined(OrderQuarantinedEvent event) {
        log.warn("Order {} (code {}) quarantined for operator review — seats remain held until "
                + "approved or denied", event.orderId(), event.referenceCode());
    }
}
