package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The single, provider-agnostic place an order transitions to {@code PAID}. Every provider — Stripe
 * webhook, Interac manual confirmation, Mock — funnels its confirmation signal through here so the
 * state machine and fulfillment live in one spot. The order row is locked for the transition, so the
 * transition is idempotent and safe under concurrency: redelivered webhooks or double-clicks resolve
 * to a no-op rather than double-fulfilling.
 */
@Service
@AllArgsConstructor
public class PaymentConfirmationService {

    private final OrderRepository orderRepository;
    private final FulfillmentService fulfillmentService;
    private final InventoryService inventoryService;

    @Transactional
    public Order confirmPayment(UUID orderId, String providerRef) {
        // Lock the order row: serializes concurrent confirmations and cancel/expiry transitions.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Idempotent: payment has already been processed (fulfilled, or queued for refund).
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.REFUND_PENDING) {
            return order;
        }

        switch (order.getStatus()) {
            case AWAITING_PAYMENT -> { /* normal path: fall through to fulfill below */ }
            case EXPIRED, CANCELLED -> {
                // Payment landed after the hold was released (common with slow e-transfers). Try to
                // re-acquire the seats; if they're gone, the captured funds must be refunded.
                if (!reReserveSeats(order)) {
                    if (providerRef != null) {
                        order.setProviderRef(providerRef);
                    }
                    order.setStatus(OrderStatus.REFUND_PENDING);
                    return orderRepository.save(order);
                }
                // seats re-acquired → fall through to fulfill below
            }
            default -> throw new ConflictException(
                "Order " + orderId + " cannot be paid from status " + order.getStatus());
        }

        if (providerRef != null) {
            order.setProviderRef(providerRef);
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        fulfillmentService.fulfill(order);
        return orderRepository.save(order);
    }

    /** Re-acquires the seats an expired/cancelled order had held. {@code false} if any is sold out. */
    private boolean reReserveSeats(Order order) {
        return inventoryService.tryReserveAll(InventoryService.seatsByTicketType(order.getItems()));
    }
}
