package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class OrderItemResponse {
    private UUID id;
    private UUID ticketTypeId;
    private String ticketTypeName;
    private String attendeeFirstName;
    private String attendeeLastName;
    private String attendeeEmail;
    private BigDecimal unitPrice;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .ticketTypeId(item.getTicketType() != null ? item.getTicketType().getId() : null)
                .ticketTypeName(item.getTicketType() != null ? item.getTicketType().getName() : null)
                .attendeeFirstName(item.getAttendeeFirstName())
                .attendeeLastName(item.getAttendeeLastName())
                .attendeeEmail(item.getAttendeeEmail())
                .unitPrice(item.getUnitPrice())
                .build();
    }
}
