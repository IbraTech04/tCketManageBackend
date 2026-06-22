package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of the event-creation wizard: the created event (with its zones) plus the ticket types
 * created alongside it. Zones carry their real UUIDs so the frontend can map its wizard keys back
 * to persisted zones, and each ticket type's entitlements reference those zone UUIDs.
 */
@Data
@Builder
@AllArgsConstructor
public class FullEventResponse {

    private EventResponse event;
    private List<TicketTypeResponse> ticketTypes;

    public static FullEventResponse from(Event event, List<TicketType> ticketTypes) {
        List<TicketTypeResponse> types = ticketTypes == null ? List.of()
                : ticketTypes.stream()
                    .map(TicketTypeResponse::from)
                    .collect(Collectors.toList());
        return FullEventResponse.builder()
                .event(EventResponse.from(event))
                .ticketTypes(types)
                .build();
    }
}
