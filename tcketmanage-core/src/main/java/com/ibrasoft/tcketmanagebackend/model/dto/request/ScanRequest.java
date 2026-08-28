package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for scanning a ticket at a zone
 * Used to initiate a scan operation with ticket and zone information
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ScanRequest {
    // Getters and setters
    @NotNull
    private UUID ticketId;

    @NotNull
    private UUID zoneId;
}