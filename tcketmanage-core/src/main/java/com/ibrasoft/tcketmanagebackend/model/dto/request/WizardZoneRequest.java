package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A zone defined inside the event-creation wizard. The {@code key} is a client-supplied alias
 * (unique within the request) used by {@link WizardEntitlementRequest} to reference this zone
 * before its real UUID has been assigned.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WizardZoneRequest {

    /** Client-supplied reference key, unique within the request (e.g. "vip", "ga", "0"). */
    @NotBlank
    private String key;

    @NotBlank
    @Size(min = 1, max = 20)
    private String name;

    /** Optional human description; defaults to the zone name when omitted. */
    private String description;
}
