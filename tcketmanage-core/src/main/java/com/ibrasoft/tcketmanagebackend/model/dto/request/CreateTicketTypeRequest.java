package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.Valid;
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
 * Request DTO for creating a ticket type along with its per-zone entitlements. The owning event is
 * taken from the request path ({@code POST /events/{id}/ticket-types}).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketTypeRequest {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    private Boolean isActive = true;

    private Instant salesStartAt;

    private Instant salesEndAt;

    @Valid
    private List<ZoneEntitlementRequest> entitlements = new ArrayList<>();
}
