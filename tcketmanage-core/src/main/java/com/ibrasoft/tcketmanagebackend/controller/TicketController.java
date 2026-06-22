package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobAccepted;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketResponse;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.service.TicketDeliveryService;
import com.ibrasoft.tcketmanagebackend.service.TicketService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Individual tickets. Most tickets are created via paid orders or CSV import; {@code POST /} here
 * issues a single comp/admin ticket directly. Listing tickets for an event lives at
 * {@code GET /events/{id}/tickets}.
 */
@RestController
@RequestMapping("/api/v1/tickets")
@AllArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final TicketDeliveryService ticketDeliveryService;

    // SECURITY (capability-URL): the unguessable ticket UUID authorizes retrieval — holding it is the
    // permission. Intentionally open so a holder can fetch their own ticket (incl. guest checkout).
    // Core has no user model, so it cannot verify the caller *owns* this ticket; ownership is the
    // embedding host's concern. A host needing real enforcement should add a host-provided access
    // check (optional access-verifier bean / @PostAuthorize), not a role guard here.
    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.findTicketById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Ticket created = ticketService.createTicket(
            request.getFirstName(), request.getLastName(), request.getEmail(),
            request.getEventId(), request.getTicketTypeId());
        // Opt-in delivery: dispatched asynchronously, so the returned ticket's lastTicketSent is
        // still null here — delivery is stamped once the email pool sends it. Comp tickets created
        // without sendEmail stay silent (and "missing") by default.
        if (request.isSendEmail()) {
            ticketDeliveryService.sendAsync(created.getID());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(created));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PutMapping("/{id}")
    public TicketResponse updateTicket(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return TicketResponse.from(ticketService.updateTicket(id, request));
    }

    /**
     * Revokes a ticket: it is marked {@code REVOKED} (kept for the audit trail, no longer scannable)
     * and its seat is returned to inventory. Prefer this over {@link #deleteTicket} for any ticket
     * that legitimately existed — revocation preserves the paper trail.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/revoke")
    public TicketResponse revokeTicket(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.revokeTicket(id));
    }

    /**
     * Reactivates a revoked/cancelled ticket back to {@code ACTIVE}, re-reserving its seat. Returns
     * {@code 409} if the ticket type is now sold out.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/reactivate")
    public TicketResponse reactivateTicket(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.reactivateTicket(id));
    }

    /**
     * Hard-deletes a ticket, erasing it entirely. Reserved for genuinely erroneous records (e.g. a
     * test ticket) — for normal voiding use {@link #revokeTicket}, which preserves the audit trail and
     * releases the seat.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable UUID id) {
        return ticketService.deleteTicket(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Re-emails a single ticket to its holder (e.g. a manually issued ticket the attendee never
     * received). Returns {@code 202 Accepted} with a job handle; delivery happens asynchronously and
     * its progress/outcome is published over STOMP at {@code /topic/email-jobs/{jobId}}.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/resend")
    public ResponseEntity<EmailJobAccepted> resendTicket(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(ticketDeliveryService.resend(id));
    }
}
