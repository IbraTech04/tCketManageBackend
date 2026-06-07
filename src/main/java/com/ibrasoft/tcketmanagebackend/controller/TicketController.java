package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketResponse;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.security.AdminGuard;
import com.ibrasoft.tcketmanagebackend.service.TicketDeliveryService;
import com.ibrasoft.tcketmanagebackend.service.TicketService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AdminGuard adminGuard;

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable UUID id) {
        return TicketResponse.from(ticketService.findTicketById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Ticket created = ticketService.createTicket(
            request.getFirstName(), request.getLastName(), request.getEmail(),
            request.getEventId(), request.getTicketTypeId());
        // Opt-in delivery: emails the ticket and stamps lastTicketSent on the same instance, so the
        // response reflects delivery. Left silent (and "missing") by default for comp tickets.
        if (request.isSendEmail()) {
            ticketDeliveryService.send(created);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(created));
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(@PathVariable UUID id, @Valid @RequestBody UpdateTicketRequest request) {
        return TicketResponse.from(ticketService.updateTicket(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable UUID id) {
        return ticketService.deleteTicket(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Re-emails a single ticket to its holder (e.g. a manually issued ticket the attendee never
     * received). Returns the ticket with its refreshed {@code lastTicketSent}.
     */
    @PostMapping("/{id}/resend")
    public TicketResponse resendTicket(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        adminGuard.require(adminToken);
        return TicketResponse.from(ticketDeliveryService.resend(id));
    }
}
