package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for updating an existing ticket.
 *
 * <p>Every field is optional and this is a <em>partial</em> update: a field left out (or sent as
 * {@code null}) leaves the stored value alone. That is why none of these carry {@code @NotBlank} —
 * absence is legal here even though {@link com.ibrasoft.tcketmanagebackend.model.ticket.Ticket}
 * requires all three holder fields to be present on the persisted row.
 *
 * <p>SECURITY: "optional" must not mean "unvalidated". A supplied value has to satisfy the same
 * shape the entity does, checked here so it fails as a 400 with a field name rather than as a
 * flush-time {@code ConstraintViolationException} — or, worse, not at all. The {@code @Pattern}
 * guards close the hole {@code @Size(min = 1)} leaves open: {@code "   "} passes a minimum-length
 * check and {@code ""} passes {@code @Email}, so without them a caller could still wipe a holder's
 * name or blank the address the signed-QR resend mails to. {@code eventId} is deliberately absent —
 * moving a ticket between events is a cancel-and-reissue, not a field edit.
 *
 * <p>Values are trimmed as they are bound, not after validation, because the constraints above are
 * only meaningful against the value that will actually be stored. Hibernate Validator's
 * {@code @Email} matches the local part against an atom class that excludes spaces, so
 * {@code " jane@example.com "} would be a 400 rather than a trim; and {@code @Size(max = 50)}
 * evaluated pre-trim would reject a padded name that fits perfectly once trimmed. Normalising in
 * the setters (which is what Jackson binds through, before {@code @Valid} runs) makes both
 * constraints judge the stored value. The all-args constructor delegates to the setters so a
 * programmatic caller gets identical normalisation.
 */
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

    /**
     * Ticket type to move this ticket to. Must belong to the ticket's own event; the transfer moves
     * the ticket's seat between the two types' capacities and is rejected if the target is sold out
     * (see {@code TicketService.updateTicket}).
     */
    private UUID ticketTypeId;

    /**
     * Default constructor for Jackson deserialization
     */
    public UpdateTicketRequest() {}

    /**
     * Constructor with all fields
     * @param firstName updated first name of the ticket holder, or {@code null} to leave unchanged
     * @param lastName updated last name of the ticket holder, or {@code null} to leave unchanged
     * @param email updated email address of the ticket holder, or {@code null} to leave unchanged
     * @param ticketTypeId updated ticket type ID for this ticket, or {@code null} to leave unchanged
     */
    public UpdateTicketRequest(String firstName, String lastName, String email, UUID ticketTypeId) {
        // Via the setters, so this path normalises exactly as Jackson binding does.
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        this.ticketTypeId = ticketTypeId;
    }

    /** Trims a bound value, preserving {@code null} as "field not supplied". */
    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    // Getters and setters
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = normalize(firstName);
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = normalize(lastName);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalize(email);
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public void setTicketTypeId(UUID ticketTypeId) {
        this.ticketTypeId = ticketTypeId;
    }
}
