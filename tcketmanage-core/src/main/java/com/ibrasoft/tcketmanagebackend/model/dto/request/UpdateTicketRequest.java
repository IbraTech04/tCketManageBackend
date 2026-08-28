package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for updating an existing ticket.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketRequest {

    /** Requires at least one non-whitespace character; {@code null} means "leave unchanged". */
    private static final String NOT_BLANK = ".*\\S.*";

    // Getters and setters
    @Size(max = 50)
    @Pattern(regexp = NOT_BLANK, message = "firstName must not be blank")
    private String firstName;

    @Size(max = 50)
    @Pattern(regexp = NOT_BLANK, message = "lastName must not be blank")
    private String lastName;

    @Size(max = 255)
    @Email
    @Pattern(regexp = NOT_BLANK, message = "email must not be blank")
    private String email;

    /**
     * Ticket type to move this ticket to. Must belong to the ticket's own event; the transfer moves
     * the ticket's seat between the two types' capacities and is rejected if the target is sold out
     * (see {@code TicketService.updateTicket}).
     */
    @Setter
    private UUID ticketTypeId;


    /* Not lombok for normalization purposes */
    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    public void setFirstName(String firstName) {
        this.firstName = normalize(firstName);
    }

    public void setLastName(String lastName) {
        this.lastName = normalize(lastName);
    }

    public void setEmail(String email) {
        this.email = normalize(email);
    }
}
