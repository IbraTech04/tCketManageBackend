package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    /**
     * Default constructor for Jackson deserialization
     */
    public CreateTicketRequest() {}

    /**
     * Constructor with all fields
     * @param firstName first name of the ticket holder
     * @param lastName last name of the ticket holder
     * @param email email address of the ticket holder
     * @param eventId ID of the event this ticket is for
     * @param ticketTypeId ID of the ticket type for this ticket
     */
    public CreateTicketRequest(String firstName, String lastName, String email, UUID eventId, UUID ticketTypeId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.eventId = eventId;
        this.ticketTypeId = ticketTypeId;
    }

    // Getters and setters
    public String getFirstName() { 
        return firstName; 
    }
    
    public void setFirstName(String firstName) { 
        this.firstName = firstName; 
    }
    
    public String getLastName() { 
        return lastName; 
    }
    
    public void setLastName(String lastName) { 
        this.lastName = lastName; 
    }
    
    public String getEmail() { 
        return email; 
    }
    
    public void setEmail(String email) { 
        this.email = email; 
    }
    
    public UUID getEventId() { 
        return eventId; 
    }
    
    public void setEventId(UUID eventId) { 
        this.eventId = eventId; 
    }
    
    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public void setTicketTypeId(UUID ticketTypeId) {
        this.ticketTypeId = ticketTypeId;
    }

    public boolean isSendEmail() {
        return sendEmail;
    }

    public void setSendEmail(boolean sendEmail) {
        this.sendEmail = sendEmail;
    }
}