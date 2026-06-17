package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class ScanEventResponse {
    private UUID id;
    private UUID ticketId;
    private UUID zoneId;
    private String zoneName;
    private LocalDateTime timestamp;

    public static ScanEventResponse from(ScanEvent scanEvent) {
        return ScanEventResponse.builder()
                .id(scanEvent.getId())
                .ticketId(scanEvent.getTicketId())
                .zoneId(scanEvent.getZone() != null ? scanEvent.getZone().getId() : null)
                .zoneName(scanEvent.getZone() != null ? scanEvent.getZone().getName() : null)
                .timestamp(scanEvent.getTimestamp())
                .build();
    }
}
