package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class TicketTypeResponse {
    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal price;
    private Boolean isActive;
    private Instant createdAt;
    private Instant salesStartAt;
    private Instant salesEndAt;
    private List<ZoneEntitlementResponse> entitlements;

    public static TicketTypeResponse from(TicketType ticketType) {
        List<ZoneEntitlementResponse> entitlements = ticketType.getEntitlements() == null ? List.of()
                : ticketType.getEntitlements().stream()
                    .map(ZoneEntitlementResponse::from)
                    .collect(Collectors.toList());
        return TicketTypeResponse.builder()
                .id(ticketType.getId())
                .eventId(ticketType.getEvent() != null ? ticketType.getEvent().getId() : null)
                .name(ticketType.getName())
                .price(ticketType.getPrice())
                .isActive(ticketType.getIsActive())
                .createdAt(ticketType.getCreatedAt())
                .salesStartAt(ticketType.getSalesStartAt())
                .salesEndAt(ticketType.getSalesEndAt())
                .entitlements(entitlements)
                .build();
    }
}
