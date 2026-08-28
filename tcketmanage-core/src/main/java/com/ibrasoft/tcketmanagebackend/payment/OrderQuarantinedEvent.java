package com.ibrasoft.tcketmanagebackend.payment;

import java.util.UUID;

/**
 * Published when an order is set aside for operator review (a received payment couldn't be cleanly
 * matched to it). The seats stay held until the operator approves or denies the quarantine, so this
 * event is the hook point for alerting someone to act — a host can listen for it to send an email,
 * raise a dashboard badge, etc. Core ships only a logging listener
 * ({@code OrderQuarantinedListener}); richer delivery is the embedding host's concern.
 *
 * @param orderId       the quarantined order
 * @param referenceCode the order's human-facing reference code (the e-Transfer memo)
 */
public record OrderQuarantinedEvent(UUID orderId, String referenceCode) {
}
