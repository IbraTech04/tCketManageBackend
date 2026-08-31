package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional half of async email delivery, kept in a bean separate from
 * {@link EmailDispatchService} so its {@code @Transactional} boundaries actually apply — a
 * {@code @Transactional} method self-invoked on the async bean would be bypassed by the proxy.
 * (Named for the same reason as {@code OrderTransactions}, which exists for the same reason.)
 *
 * <p>Crucially, no DB transaction is held across the SMTP send. Each method opens a short
 * transaction and returns a detached entity; the caller does the slow SMTP work with no transaction
 * open, then {@link #markTicketSent} opens a second short transaction to stamp delivery. This avoids
 * tying up a DB connection for the duration of every SMTP round-trip.
 *
 * <p>The two load methods differ in what they must fetch, which is the one thing worth knowing here:
 * a {@link Ticket}'s {@code event}/{@code ticketType} are {@code @ManyToOne} EAGER and come along on
 * a plain {@code findById}, but {@code Order.items} is a lazy {@code @OneToMany}, so
 * {@link #loadOrder} must fetch-join it or the template throws
 * {@code LazyInitializationException} the moment it iterates the line items.
 */
@Service
@AllArgsConstructor
public class EmailTransactions {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;

    /** Loads a ticket for rendering, or empty if it no longer exists. Returns a detached entity. */
    @Transactional(readOnly = true)
    public Optional<Ticket> loadTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId);
    }

    /** Stamps {@code lastTicketSent} after a confirmed successful delivery. */
    @Transactional
    public void markTicketSent(UUID ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            ticket.setLastTicketSent(Instant.now());
            ticketRepository.save(ticket);
        });
    }

    /**
     * Loads an order with its {@code items} initialized, or empty if it no longer exists. Detached.
     * {@code event} and each item's {@code ticketType} are eager {@code @ManyToOne}s and come along
     * with the fetch join.
     */
    @Transactional(readOnly = true)
    public Optional<Order> loadOrder(UUID orderId) {
        return orderRepository.findByIdWithItems(orderId);
    }
}
