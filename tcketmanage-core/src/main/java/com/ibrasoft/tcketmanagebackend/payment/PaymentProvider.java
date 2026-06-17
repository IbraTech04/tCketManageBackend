package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.model.order.Order;

import java.time.Duration;

/**
 * A pluggable payment method. Implementations are Spring beans guarded by
 * {@code @ConditionalOnProperty("payments.<id>.enabled")}, so a deployment activates only the
 * providers it has configured. The order state machine talks only to this interface and to
 * {@link PaymentConfirmationService}, never to a concrete provider.
 */
public interface PaymentProvider {

    /** Stable identifier used in config and on the order (e.g. "mock", "stripe", "interac"). */
    String id();

    /** Begins payment for an order and describes how the buyer should proceed. */
    PaymentInitiation initiate(PaymentContext context);

    /** How long an order using this provider should hold inventory while awaiting payment. */
    Duration holdDuration();

    /**
     * Whether payment is confirmed automatically via webhook/callback (true, e.g. Stripe) or
     * requires an operator to confirm out-of-band receipt (false, e.g. Interac e-Transfer).
     */
    boolean isAutomatic();

    /** Attempts to refund a paid order. May complete automatically or require manual action. */
    RefundOutcome refund(Order order);
}
