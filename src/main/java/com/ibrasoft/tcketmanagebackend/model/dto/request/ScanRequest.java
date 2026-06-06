package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.util.UUID;

/**
 * Request DTO for scanning a ticket at a zone
 * Used to initiate a scan operation with ticket and zone information
 */
public class ScanRequest {
    private UUID ticketId;
    private UUID zoneId;

    /**
     * Default constructor for Jackson deserialization
     */
    public ScanRequest() {}

    /**
     * Constructor with all fields
     * @param ticketId ID of the ticket to scan
     * @param zoneId ID of the zone where the scan occurs
     */
    public ScanRequest(UUID ticketId, UUID zoneId) {
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