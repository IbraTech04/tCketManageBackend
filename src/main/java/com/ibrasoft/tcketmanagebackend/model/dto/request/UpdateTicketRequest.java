package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.util.UUID;

/**
 * Request DTO for updating an existing ticket
 * Contains fields that can be modified on an existing ticket
 */
public class UpdateTicketRequest {
    private String firstName;
    private String lastName;
    private String email;
    private UUID ticketTypeId;

    /**
     * Default constructor for Jackson deserialization
     */
    public UpdateTicketRequest() {}

    /**
     * Constructor with all fields
     * @param firstName updated first name of the ticket holder
     * @param lastName updated last name of the ticket holder
     * @param email updated email address of the ticket holder
     * @param ticketTypeId updated ticket type ID for this ticket
     */
    public UpdateTicketRequest(String firstName, String lastName, String email, UUID ticketTypeId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
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
    
    public UUID getTicketTypeId() { 
        return ticketTypeId; 
    }
    
    public void setTicketTypeId(UUID ticketTypeId) { 
        this.ticketTypeId = ticketTypeId; 
    }
}