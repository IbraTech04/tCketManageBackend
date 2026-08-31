package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
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
    private Instant createdAt;
    private Instant expiresAt;
    private Instant paidAt;
    private List<OrderItemResponse> items;

    /** Present on creation: how the buyer should pay. Null on plain reads. */
    private PaymentResponse payment;

    /**
     * Provider-side reference for the settled payment - for Interac, the reference number printed on
     * the e-Transfer notification. Null until the order is paid.
     */
    private String providerRef;

    /**
     * The payer's name as their bank reported it on the e-Transfer. Null for every other provider,
     * and until an e-Transfer has been matched to this order.
     */
    private String etransferSenderName;

    /** When the e-Transfer notification reached our mailbox. Distinct from {@link #paidAt}, which is
     * when we settled the order. */
    private Instant etransferReceivedAt;

    public static OrderResponse from(Order order) {
        return from(order, null, null);
    }

    public static OrderResponse from(Order order, PaymentInitiation initiation) {
        return from(order, initiation, null);
    }

    /**
     * @param receipt the most recent e-Transfer receipt for this order, or {@code null} when there is
     *                none (any other provider, or no payment seen yet). Passed in rather than
     *                navigated from {@link Order} so a list endpoint can resolve receipts for a whole
     *                page in one query instead of one per row.
     */
    public static OrderResponse from(Order order, PaymentInitiation initiation, EtransferReceipt receipt) {
        List<OrderItemResponse> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(OrderItemResponse::from).collect(Collectors.toList());
        return OrderResponse.builder()
                .providerRef(order.getProviderRef())
                .etransferSenderName(receipt != null ? receipt.getSenderName() : null)
                .etransferReceivedAt(receipt != null ? receipt.getEmailReceivedAt() : null)
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
