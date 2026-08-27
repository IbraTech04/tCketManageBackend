package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for scanning a ticket at a zone
 * Used to initiate a scan operation with ticket and zone information
 *
 * <p>Both ids are mandatory: {@code ScanEventService.scanTicket} looks each one up and treats a miss
 * as "not found". Omitting one is a malformed request, not a 404, so it is rejected at the boundary.
 * Matches the constraints already on {@link QrScanRequest}, the other entry point to the same
 * service method.
 */
public class ScanRequest {
    @NotNull
    private UUID ticketId;

    @NotNull
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