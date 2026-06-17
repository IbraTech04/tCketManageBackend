package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The transactional half of async email delivery, kept separate from {@link EmailDispatchService} so
 * its {@code @Transactional} boundaries actually apply (a self-invoked {@code @Transactional} method
 * on the async bean would be bypassed by the proxy).
 *
 * <p>Crucially, the DB transaction is <em>not</em> held across the SMTP send: {@link #load} opens a
 * short read transaction to fetch the ticket (its {@code event}/{@code ticketType} are
 * {@code @ManyToOne} EAGER, so the returned detached entity renders fine), the caller does the slow
 * SMTP work with no transaction open, then {@link #markSent} opens a second short transaction to
 * stamp delivery. This avoids tying up a DB connection for the duration of each SMTP round-trip.
 */
@Service
@AllArgsConstructor
public class TicketEmailSender {

    private final TicketRepository ticketRepository;

    /** Loads the ticket for rendering, or empty if it no longer exists. Returns a detached entity. */
    @Transactional(readOnly = true)
    public Optional<Ticket> load(UUID ticketId) {
        return ticketRepository.findById(ticketId);
    }

    /** Stamps {@code lastTicketSent} after a confirmed successful delivery. */
    @Transactional
    public void markSent(UUID ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            ticket.setLastTicketSent(Instant.now());
            ticketRepository.save(ticket);
        });
    }
}
