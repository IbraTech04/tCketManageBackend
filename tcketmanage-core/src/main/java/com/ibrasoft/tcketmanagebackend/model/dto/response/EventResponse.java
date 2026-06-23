package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class EventResponse {
    private UUID id;
    private String name;
    private OffsetDateTime time;
    private String location;
    private String description;
    private List<ZoneResponse> zones;

    public static EventResponse from(Event event) {
        List<ZoneResponse> zones = event.getZones() == null ? List.of()
                : event.getZones().stream()
                    .map(ZoneResponse::from)
                    .collect(Collectors.toList());
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .time(event.getTime())
                .location(event.getLocation())
                .description(event.getDescription())
                .zones(zones)
                .build();
    }
}
