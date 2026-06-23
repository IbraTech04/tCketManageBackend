package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Atomic "event creation wizard" payload: event metadata, its zones, and its ticket types (with
 * per-zone entitlements) in a single request. The whole graph is persisted in one transaction by
 * {@code POST /tcket/events/full}; if any part fails validation, nothing is created.
 *
 * <p>Because zones and ticket types are created together, ticket-type entitlements cannot reference
 * zones by UUID (the UUIDs don't exist yet). Instead each {@link WizardZoneRequest} carries a
 * client-supplied {@code key}, and {@link WizardEntitlementRequest#getZoneKey()} references that
 * key. The backend generates the real zone UUIDs and resolves the keys during creation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateFullEventRequest {

    @NotBlank
    private String name;

    @NotNull
    private OffsetDateTime time;

    @NotBlank
    private String location;

    @NotBlank
    private String description;

    /** At least one zone is required; zone {@code key}s must be unique within the request. */
    @Valid
    @NotEmpty
    private List<WizardZoneRequest> zones = new ArrayList<>();

    /** Ticket types are optional — an event can be created with zones only and priced later. */
    @Valid
    private List<WizardTicketTypeRequest> ticketTypes = new ArrayList<>();
}
