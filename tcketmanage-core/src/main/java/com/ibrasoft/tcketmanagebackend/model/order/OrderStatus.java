package com.ibrasoft.tcketmanagebackend.model.order;

/**
 * Lifecycle states of an {@link Order}.
 *
 * <pre>
 * AWAITING_PAYMENT ──confirm──▶ PAID ──refund──▶ REFUNDED
 *        │                       ▲
 *        ├──expiry sweep──▶ EXPIRED ─┐
 *        ├──buyer cancel──▶ CANCELLED ┤ confirm (payment landed late):
 *        │                            ├─ seats re-acquired ──▶ PAID
 *        │                            └─ sold out ──▶ REFUND_PENDING
 * </pre>
 *
 * {@code REFUND_PENDING} means funds were captured but inventory was already gone (the buyer paid
 * after the hold expired/was cancelled and the seats had been resold); the order is queued for an
 * operator or provider refund.
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    QUARANTINED,
}
