package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobAccepted;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobStatus;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.service.email.EmailDispatchService;
import com.ibrasoft.tcketmanagebackend.service.email.EmailJobRegistry;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Owns ticket email delivery beyond initial order fulfillment: operator-triggered resends and the
 * "send missing" flow. All delivery here is asynchronous — methods return immediately and the actual
 * sending (plus stamping {@code lastTicketSent}) happens on the email pool. Bulk and operator-facing
 * flows return an {@link EmailJobAccepted} handle so the caller can follow progress over STOMP
 * ({@code /topic/email-jobs/{jobId}}); the original-order fulfillment path dispatches fire-and-forget.
 */
@Service
@AllArgsConstructor
public class TicketDeliveryService {

    private final EmailJobRegistry emailJobRegistry;
    private final EmailDispatchService emailDispatchService;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    /** Fire-and-forget delivery of a single ticket (e.g. an opt-in send when issuing a comp ticket). */
    public void sendAsync(UUID ticketId) {
        emailDispatchService.sendInBackground(ticketId);
    }

    /** Resends a single ticket by id as a tracked (size-1) job. 404s if the ticket doesn't exist. */
    public EmailJobAccepted resend(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket not found");
        }
        return submit("RESEND_ONE", List.of(ticketId));
    }

    /** Resends every ticket for an event (e.g. after a CSV import) as a tracked job. */
    public EmailJobAccepted resendAll(UUID eventId) {
        requireEvent(eventId);
        return submit("RESEND_ALL", ticketRepository.findIdsByEvent_Id(eventId));
    }

    /** Sends only the event's tickets that have never been successfully emailed, as a tracked job. */
    public EmailJobAccepted sendMissing(UUID eventId) {
        requireEvent(eventId);
        return submit("SEND_MISSING", ticketRepository.findIdsByEvent_IdAndLastTicketSentIsNull(eventId));
    }

    /**
     * Registers a tracked job and kicks off its async batch, returning a handle immediately so the
     * HTTP caller never blocks on delivery. The {@code emailDispatchService.runJob} call crosses a
     * bean boundary on purpose — that's what lets its {@code @Async} proxy take effect.
     */
    private EmailJobAccepted submit(String type, List<UUID> ticketIds) {
        EmailJobStatus status = emailJobRegistry.create(type, ticketIds.size());
        emailDispatchService.runJob(status, ticketIds);
        return new EmailJobAccepted(status.getJobId(), status.getTotal());
    }

    private void requireEvent(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found");
        }
    }
}
