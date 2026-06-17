package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A single per-zone access grant for a ticket type: which zone, and how many entries it allows.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ZoneEntitlementRequest {

    @NotNull
    private UUID zoneId;

    @Min(1)
    private Integer maxEntries;
}
