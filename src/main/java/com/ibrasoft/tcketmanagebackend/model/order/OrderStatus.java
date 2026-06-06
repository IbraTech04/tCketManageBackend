package com.ibrasoft.tcketmanagebackend.model.order;

/**
 * Lifecycle states of an {@link Order}.
 *
 * <pre>
 * AWAITING_PAYMENT ──confirm──▶ PAID ──refund──▶ REFUNDED
 *        │
 *        ├──expiry sweep──▶ EXPIRED
 *        └──buyer cancel──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED,
    REFUNDED
}
