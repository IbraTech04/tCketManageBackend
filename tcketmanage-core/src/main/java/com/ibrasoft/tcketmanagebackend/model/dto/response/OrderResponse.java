package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String buyerEmail;
    private String externalRef;
    private UUID eventId;
    private String status;
    private String providerId;
    private String referenceCode;
    private BigDecimal amountTotal;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private List<OrderItemResponse> items;

    /** Present on creation: how the buyer should pay. Null on plain reads. */
    private PaymentResponse payment;

    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, PaymentInitiation initiation) {
        List<OrderItemResponse> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(OrderItemResponse::from).collect(Collectors.toList());
        return OrderResponse.builder()
                .id(order.getId())
                .buyerEmail(order.getBuyerEmail())
                .externalRef(order.getExternalRef())
                .eventId(order.getEvent() != null ? order.getEvent().getId() : null)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .providerId(order.getProviderId())
                .referenceCode(order.getReferenceCode())
                .amountTotal(order.getAmountTotal())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .items(items)
                .payment(initiation != null ? PaymentResponse.from(initiation) : null)
                .build();
    }
}
