package com.ibrasoft.tcketmanagebackend.model.ticket;

/**
 * State of an issued {@link Ticket}. Tickets are created {@code ACTIVE} on payment and moved to
 * {@code CANCELLED} on refund/cancellation.
 */
public enum TicketStatus {
    ACTIVE,
    CANCELLED
}
