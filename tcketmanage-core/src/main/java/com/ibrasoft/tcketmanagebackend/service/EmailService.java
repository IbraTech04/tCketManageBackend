package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;

/**
 * Delivers an issued ticket to its holder. Kept behind an interface so a real SMTP + signed-QR
 * implementation can be swapped in per deployment without touching the callers (order fulfillment
 * and the operator resend flows).
 */
public interface EmailService {

    /**
     * Sends a single ticket to its holder's email, with the rendered QR ticket attached.
     *
     * @return {@code true} if delivery succeeded, {@code false} if it failed (callers use this to
     *         decide whether to stamp {@code lastTicketSent}). Implementations must not throw on
     *         delivery failure — tickets are already issued by the time we're called.
     */
    boolean sendTicket(Ticket ticket);
}
