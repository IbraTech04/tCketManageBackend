package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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

    /**
     * Sets an order aside for an operator after a received payment couldn't be cleanly matched to it
     * (e.g. an e-Transfer arrived referencing this order's code but for the wrong amount). Locks the
     * row like {@link #confirmPayment} so a quarantine can't race a confirmation, and only transitions
     * from {@code AWAITING_PAYMENT}: an order that already settled, expired, cancelled, or was itself
     * already quarantined is left untouched, making a redelivered/duplicate email a no-op.
     *
     * <p>Crucially, this does <em>not</em> release the order's inventory hold: the seats stay reserved
     * ("spot taken") until an operator resolves the review via {@link #approveQuarantine} or
     * {@link #denyQuarantine}. A quarantined order is therefore also exempt from the expiry sweep,
     * which only targets {@code AWAITING_PAYMENT}.
     */
    @Transactional
    public Order quarantineOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return order;
        }
        order.setStatus(OrderStatus.QUARANTINED);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderQuarantinedEvent(saved.getId(), saved.getReferenceCode()));
        return saved;
    }

    /**
     * Operator decision: the quarantined payment is legitimate. The seats were held through the
     * quarantine (never released), so this simply fulfills and settles — no re-reservation needed.
     * Row-locked and idempotent: an order already {@code PAID}/{@code REFUND_PENDING} is a no-op, and
     * an order that is not {@code QUARANTINED} (e.g. denied in the meantime) is rejected.
     */
    @Transactional
    public Order approveQuarantine(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.REFUND_PENDING) {
            return order;
        }
        if (order.getStatus() != OrderStatus.QUARANTINED) {
            throw new ConflictException(
                "Order " + orderId + " is not quarantined (status " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        fulfillmentService.fulfill(order);
        return orderRepository.save(order);
    }

    /**
     * Operator decision: the quarantined payment is not valid for this order. Releases the held seats
     * back to inventory and, depending on whether money actually arrived, either cancels the order
     * ({@code fundsReceived == false}) or queues it for an out-of-band refund
     * ({@code fundsReceived == true}). Row-locked and idempotent: an order already settled into a
     * denial outcome ({@code CANCELLED}/{@code REFUND_PENDING}) is a no-op; any other non-quarantined
     * status is rejected.
     *
     * @param fundsReceived whether the (mismatched) payment was actually received and must be returned
     */
    @Transactional
    public Order denyQuarantine(UUID orderId, boolean fundsReceived) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUND_PENDING) {
            return order;
        }
        if (order.getStatus() != OrderStatus.QUARANTINED) {
            throw new ConflictException(
                "Order " + orderId + " is not quarantined (status " + order.getStatus() + ")");
        }
        inventoryService.releaseAll(InventoryService.seatsByTicketType(order.getItems()));
        order.setStatus(fundsReceived ? OrderStatus.REFUND_PENDING : OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /** Re-acquires the seats an expired/cancelled order had held. {@code false} if any is sold out. */
    private boolean reReserveSeats(Order order) {
        return inventoryService.tryReserveAll(InventoryService.seatsByTicketType(order.getItems()));
    }
}
