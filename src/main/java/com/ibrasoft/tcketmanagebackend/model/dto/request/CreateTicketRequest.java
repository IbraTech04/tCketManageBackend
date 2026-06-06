package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.util.UUID;

/**
 * Request DTO for creating a new ticket
 * Contains all required information to create a ticket for an event
 */
public class CreateTicketRequest {
    private String firstName;
    private String lastName;
    private String email;
    private UUID eventId;
    private UUID ticketTypeId;

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
}