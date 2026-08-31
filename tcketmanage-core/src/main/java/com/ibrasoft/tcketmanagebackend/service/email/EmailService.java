package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;

/**
 * Delivers issued tickets to their holders, and order-level notices to buyers. Kept behind an
 * interface so a real SMTP + signed-QR implementation can be swapped in per deployment without
 * touching the callers (order fulfillment, the expiry sweep, and the operator resend flows).
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

    /**
     * Sends the buyer a notice that their order ended (or stalled) without tickets being issued.
     *
     * @param order        the order the notice is about, with its {@code items} already initialized
     *                     — implementations render outside a transaction and cannot fault them in
     * @param notification which notice to send; supplies the subject and body copy
     * @return {@code true} if delivery succeeded, {@code false} if it failed. Like
     *         {@link #sendTicket}, implementations must not throw: the order has already reached
     *         its new state and released its inventory by the time we're called, so a bounced
     *         courtesy email must not be able to disturb that.
     */
    boolean sendOrderNotification(Order order, OrderNotification notification);
}
