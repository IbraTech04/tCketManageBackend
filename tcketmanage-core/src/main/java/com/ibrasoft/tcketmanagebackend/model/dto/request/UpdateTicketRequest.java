package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for updating an existing ticket.
 */
@Setter
@Getter
@NoArgsConstructor
public class UpdateTicketRequest {
    /** Requires at least one non-whitespace character; {@code null} means "leave unchanged". */
    private static final String NOT_BLANK = ".*\\S.*";

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

    private UUID ticketTypeId;

    public UpdateTicketRequest(String firstName, String lastName, String email, UUID ticketTypeId) {
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        this.ticketTypeId = ticketTypeId;
    }

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