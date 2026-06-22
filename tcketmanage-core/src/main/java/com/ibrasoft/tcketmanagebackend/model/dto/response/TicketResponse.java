package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private UUID eventId;
    private TicketTypeResponse ticketType;

    /** Lifecycle state (ACTIVE / CANCELLED / REVOKED); only ACTIVE tickets scan through. */
    private String status;

    /** When the ticket was last successfully emailed, or {@code null} if it has never been sent. */
    private Instant lastTicketSent;

    public static TicketResponse from(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getID())
                .firstName(ticket.getFirstName())
                .lastName(ticket.getLastName())
                .email(ticket.getEmail())
                .eventId(ticket.getEvent() != null ? ticket.getEvent().getId() : null)
                .ticketType(ticket.getTicketType() != null ? TicketTypeResponse.from(ticket.getTicketType()) : null)
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                .lastTicketSent(ticket.getLastTicketSent())
                .build();
    }
}
