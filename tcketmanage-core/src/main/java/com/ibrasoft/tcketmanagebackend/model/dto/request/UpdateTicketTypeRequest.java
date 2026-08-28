package com.ibrasoft.tcketmanagebackend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for updating a ticket type. The owning event cannot be changed; the entitlement
 * list, when provided, replaces the existing set.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketTypeRequest {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    private Boolean isActive = true;

    @Min(0)
    private Integer capacity;

    @JsonIgnore
    private boolean capacityPresent;

    private Instant salesStartAt;

    private Instant salesEndAt;

    @Valid
    private List<ZoneEntitlementRequest> entitlements = new ArrayList<>();

    /**
     * Records that the caller supplied a capacity — including an explicit {@code null} — before
     * storing it. Hand-written rather than Lombok-generated precisely so this bookkeeping happens;
     * see {@link #capacityPresent}.
     */
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        this.capacityPresent = true;
    }
}
