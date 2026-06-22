package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.util.UUID;

/**
 * Request DTO for validating a ticket for a specific zone
 * Used to check if a ticket is valid for entry to a zone without performing the scan
 */
public class ValidateRequest {
    private UUID ticketId;
    private UUID zoneId;

    /**
     * Default constructor for Jackson deserialization
     */
    public ValidateRequest() {}

    /**
     * Constructor with all fields
     * @param ticketId ID of the ticket to validate
     * @param zoneId ID of the zone to validate against
     */
    public ValidateRequest(UUID ticketId, UUID zoneId) {
        this.ticketId = ticketId;
        this.zoneId = zoneId;
    }

    // Getters and setters
    public UUID getTicketId() { 
        return ticketId; 
    }
    
    public void setTicketId(UUID ticketId) { 
        this.ticketId = ticketId; 
    }
    
    public UUID getZoneId() { 
        return zoneId; 
    }
    
    public void setZoneId(UUID zoneId) { 
        this.zoneId = zoneId; 
    }
}