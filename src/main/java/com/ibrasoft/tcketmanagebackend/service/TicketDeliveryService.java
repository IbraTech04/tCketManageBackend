package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketDeliveryResponse;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns ticket email delivery beyond initial order fulfillment: operator-triggered resends and the
 * "send missing" flow. Centralizes the "send, and on success stamp {@code lastTicketSent}" rule so
 * every path (fulfillment, single resend, bulk resend) records delivery consistently.
 *
 * <p>Sends are intentionally <em>not</em> wrapped in one big transaction: each successful send is
 * persisted on its own, so a partial failure mid-batch still records everything that went out, and
 * a several-hundred-ticket event resend never holds a single long-running transaction open across
 * many SMTP round-trips.
 */
@Service
@AllArgsConstructor
public class TicketDeliveryService {

    private final EmailService emailService;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    /**
     * Sends a single ticket and, only if delivery succeeds, stamps {@code lastTicketSent} and saves.
     * Shared by fulfillment and the resend endpoints.
     *
     * @return {@code true} if the ticket was sent
     */
    public boolean send(Ticket ticket) {
        boolean sent = emailService.sendTicket(ticket);
        if (sent) {
            ticket.setLastTicketSent(Instant.now());
            ticketRepository.save(ticket);
        }
        return sent;
    }

    /**
     * Resends a single ticket by id (the manual-ticket "resend my ticket" flow).
     *
     * @return the ticket, with {@code lastTicketSent} updated if delivery succeeded
     */
    public Ticket resend(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        send(ticket);
        return ticket;
    }

    /** Resends every ticket for an event (e.g. after a CSV import). */
    public TicketDeliveryResponse resendAll(UUID eventId) {
        requireEvent(eventId);
        return deliver(ticketRepository.findByEvent_Id(eventId));
    }

    /** Sends only the tickets for an event that have never been successfully emailed. */
    public TicketDeliveryResponse sendMissing(UUID eventId) {
        requireEvent(eventId);
        return deliver(ticketRepository.findByEvent_IdAndLastTicketSentIsNull(eventId));
    }

    private TicketDeliveryResponse deliver(List<Ticket> tickets) {
        int sent = 0;
        for (Ticket ticket : tickets) {
            if (send(ticket)) {
                sent++;
            }
        }
        return TicketDeliveryResponse.builder()
                .total(tickets.size())
                .sent(sent)
                .failed(tickets.size() - sent)
                .build();
    }

    private void requireEvent(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found");
        }
    }
}
