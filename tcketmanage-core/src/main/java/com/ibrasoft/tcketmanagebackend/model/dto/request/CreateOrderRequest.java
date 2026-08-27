package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request to purchase one or more seats for an event. {@code providerId} is optional; the configured
 * default provider is used when omitted.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {

    /**
     * Ceiling on seats per order. There is no quantity field — one {@link OrderItemRequest} is
     * exactly one seat — so this list length <em>is</em> the seat count.
     *
     * <p>SECURITY: {@code POST /orders} is unauthenticated, and a successful create reserves every
     * requested seat for the provider's full hold window — 48 hours on Interac. Without a cap, one
     * anonymous request carrying a 50,000-element array empties an event's inventory for two days at
     * the cost of a single HTTP call, and builds a 50,000-row order graph inside the transaction that
     * holds the ticket-type row locks. 100 keeps the per-request blast radius bounded (memory, INSERT
     * volume, lock hold time) while sitting far above any real purchase; the largest legitimate group
     * booking these events see is a table of ten, and a buyer wanting more places a second order.
     *
     * <p>This bounds one request, not one sender — a determined attacker still loops. Rejected
     * alternative: doing the sender-level throttling here too. Core ships no security filters by
     * design (see the spring-security-core note in the POM), so per-IP or per-email rate limiting
     * belongs in the embedding host's filter chain; a DTO constraint cannot see across requests.
     */
    public static final int MAX_ITEMS = 100;

    @Email
    @NotBlank
    private String buyerEmail;

    @NotNull
    private UUID eventId;

    private String providerId;

    @Valid
    @NotEmpty
    @Size(max = MAX_ITEMS, message = "An order may contain at most " + MAX_ITEMS + " seats")
    private List<OrderItemRequest> items = new ArrayList<>();
}
