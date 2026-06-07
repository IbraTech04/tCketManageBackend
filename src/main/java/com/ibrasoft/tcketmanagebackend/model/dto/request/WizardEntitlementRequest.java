package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A per-zone access grant for a wizard ticket type. {@code zoneKey} references the
 * {@link WizardZoneRequest#getKey()} of a zone defined in the same request.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WizardEntitlementRequest {

    @NotBlank
    private String zoneKey;

    /** Total entries allowed into the zone (1 = single entry). {@code null} means unlimited. */
    @Min(1)
    private Integer maxEntries;
}
