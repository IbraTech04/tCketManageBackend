package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ZoneEntitlementResponse {
    private UUID zoneId;
    private String zoneName;
    private Integer maxEntries;

    public static ZoneEntitlementResponse from(ZoneEntitlement entitlement) {
        return ZoneEntitlementResponse.builder()
                .zoneId(entitlement.getZone() != null ? entitlement.getZone().getId() : null)
                .zoneName(entitlement.getZone() != null ? entitlement.getZone().getName() : null)
                .maxEntries(entitlement.getMaxEntries())
                .build();
    }
}
