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

    /**
     * Maximum seats that may be sold for this type. {@code null} means unlimited.
     *
     * <p>SECURITY: this field is what makes the oversell guard reachable. Without it every type
     * created through {@code POST /events/{id}/ticket-types} persisted {@code capacity = null}, so
     * {@code InventoryService}'s {@code capacity IS NULL OR reserved_count + :q <= capacity}
     * predicate matched unconditionally and the entire capacity mechanism was inert for those
     * types — only the event-creation wizard could set a limit. Nullable is still "unlimited"
     * (matching {@code TicketType#capacity} and {@code WizardTicketTypeRequest}); an operator opts
     * into a cap by sending a number.
     */
    @Min(0)
    private Integer capacity;

    private Instant salesStartAt;

    private Instant salesEndAt;

    @Valid
    private List<ZoneEntitlementRequest> entitlements = new ArrayList<>();
}
