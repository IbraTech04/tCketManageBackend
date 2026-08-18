package com.ibrasoft.tcketmanagebackend.model.order;

import lombok.Getter;

/**
 * The buyer-facing notices sent when an order ends — or stalls — without tickets being issued. Every
 * one of these transitions used to be silent to the buyer: the order simply stopped, and the only
 * record was an operator log line.
 *
 * <p>These are deliberately <em>not</em> a mirror of {@link OrderStatus}. Only the terminal states a
 * buyer would otherwise never hear about are represented; {@code PAID} already speaks for itself via
 * the ticket emails, and {@code AWAITING_PAYMENT}/{@code REFUNDED} are handled by the payment
 * provider's own messaging.
 *
 * <p>The copy lives here rather than in the template so a single generic
 * {@code orderNotificationEmail} shell renders all four, and so the wording is unit-testable without
 * rendering HTML.
 */
@Getter
public enum OrderNotification {

    /** The hold lapsed before payment arrived; seats were released. Nothing was charged. */
    EXPIRED(
            "Your order for %s has expired",
            "Expired",
            "#b45309",
            "This order has expired",
            "We didn't receive payment before the hold on your seats ran out, so they've been "
                    + "released back to the event.",
            "You haven't been charged. If you still want to go you can place a new order, though "
                    + "we can't promise the same seats are still available."),

    /** Cancelled by the buyer, or released because the payment provider couldn't start a payment. */
    CANCELLED(
            "Your order for %s was cancelled",
            "Cancelled",
            "#6b7280",
            "This order has been cancelled",
            "The order below has been cancelled, and the seats it was holding have gone back to "
                    + "the event.",
            "You haven't been charged. If this wasn't what you intended, you're welcome to place a "
                    + "new order."),

    /** A payment referencing this order arrived but didn't match; an operator has to resolve it. */
    QUARANTINED(
            "We're reviewing your order for %s",
            "Under review",
            "#0369a1",
            "This order needs a closer look",
            "We received a payment referencing this order, but it didn't match what we were "
                    + "expecting",
            "Your seats are still being held while we sort this out. There's nothing you need to "
                    + "do; someone will follow up with you."),

    /** Payment landed after the hold lapsed and the seats were gone — the money has to go back. */
    REFUND_PENDING(
            "A refund is on its way for your order for %s",
            "Refund pending",
            "#15803d",
            "We couldn't issue these tickets",
            "Your payment reached us after the hold on this order had already expired, and by then "
                    + "the seats had been taken. Since we can't issue the tickets, we've queued "
                    + "your payment for a refund.",
            "The refund goes back to the payment method you used. There's nothing you need to do.");

    /** Subject line with a single {@code %s} placeholder for the event name. */
    private final String subjectFormat;

    /** Short status word shown in the email's coloured pill. */
    private final String badge;

    /** Hex accent for the pill and rule, so the four notices are distinguishable at a glance. */
    private final String accentColor;

    /** Bold opening line. */
    private final String headline;

    /** What happened, in plain terms. */
    private final String message;

    /** What it means for the buyer, and whether they need to act. */
    private final String followUp;

    OrderNotification(String subjectFormat, String badge, String accentColor, String headline,
                      String message, String followUp) {
        this.subjectFormat = subjectFormat;
        this.badge = badge;
        this.accentColor = accentColor;
        this.headline = headline;
        this.message = message;
        this.followUp = followUp;
    }

    /** The subject line for this notice, with the event name filled in. */
    public String subject(String eventName) {
        return String.format(subjectFormat, eventName);
    }
}
