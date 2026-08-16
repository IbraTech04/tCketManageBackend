package com.ibrasoft.tcketmanagebackend.model.dto.request;

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
 * A ticket type defined inside the event-creation wizard, along with the zones it may enter.
 * Entitlements reference zones by their wizard {@code key} rather than UUID.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WizardTicketTypeRequest {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    private Boolean isActive = true;

    /** Maximum seats that may be sold for this type. {@code null} means unlimited. */
    @Min(0)
    private Integer capacity;

    private Instant salesStartAt;

    private Instant salesEndAt;

    @Valid
    private List<WizardEntitlementRequest> entitlements = new ArrayList<>();
}
