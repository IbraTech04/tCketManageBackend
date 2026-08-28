package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for creating a new ticket
 * Contains all required information to create a ticket for an event
 *
 * <p>SECURITY: the constraints below mirror the {@code Ticket} entity's own
 * ({@code @Size(min = 1, max = 50)} on the names, {@code @Email @NotBlank} on the address,
 * {@code @NotNull} on the ticket type). They are not redundant with it: the binding site
 * ({@code TicketController.createTicket}) already carries {@code @Valid}, so before this class had
 * any constraints that annotation validated nothing and a request with a null {@code ticketTypeId}
 * reached {@code TicketService.createTicket}, where {@code findById(null)} throws deep in the
 * persistence layer and surfaces as a 500. Validating at the boundary turns every one of those into
 * a 400 naming the offending field, and stops an over-long name from failing at Hibernate flush time
 * instead.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketRequest {
    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotNull
    private UUID eventId;

    @NotNull
    private UUID ticketTypeId;

    /**
     * When {@code true}, the ticket is emailed to its holder immediately on creation (and its
     * {@code lastTicketSent} stamped). Defaults to {@code false} so admins can issue silent/comp
     * tickets; operators can still deliver later via the resend / send-missing endpoints.
     */
    private boolean sendEmail = false;

}