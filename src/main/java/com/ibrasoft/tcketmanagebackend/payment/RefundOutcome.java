package com.ibrasoft.tcketmanagebackend.payment;

/**
 * Result of a refund attempt. Automatic providers (Stripe) complete immediately; manual providers
 * (Interac) signal that an operator must send the money back out of band.
 */
public record RefundOutcome(boolean succeeded, String message) {

    public static RefundOutcome completed() {
        return new RefundOutcome(true, "Refund completed");
    }

    public static RefundOutcome manualActionRequired(String message) {
        return new RefundOutcome(false, message);
    }
}
