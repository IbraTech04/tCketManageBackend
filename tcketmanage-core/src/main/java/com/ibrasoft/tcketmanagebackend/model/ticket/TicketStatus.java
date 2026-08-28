package com.ibrasoft.tcketmanagebackend.model.ticket;

/**
 * State of an issued {@link Ticket}.
 *
 * <ul>
 *   <li>{@code ACTIVE} — created on payment/issuance; the only state that scans through.</li>
 *   <li>{@code CANCELLED} — voided as part of an order-level refund (system/money-driven).</li>
 *   <li>{@code REVOKED} — pulled manually by an operator, independent of any refund.</li>
 * </ul>
 *
 * <p>Both non-{@code ACTIVE} states keep the row for the audit trail (never hard-deleted) and are
 * rejected at scan time. A ticket that consumed an inventory seat releases it on the move out of
 * {@code ACTIVE}, and re-reserves it on reactivation back to {@code ACTIVE}.
 */
public enum TicketStatus {
    ACTIVE,
    CANCELLED,
    REVOKED
}
