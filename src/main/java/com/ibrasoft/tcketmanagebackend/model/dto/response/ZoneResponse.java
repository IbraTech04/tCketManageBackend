package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ZoneResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID eventId;

    public static ZoneResponse from(Zone zone) {
        return ZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .description(zone.getDescription())
                .eventId(zone.getEvent() != null ? zone.getEvent().getId() : null)
                .build();
    }
}
