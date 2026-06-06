package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The single, provider-agnostic place an order transitions to {@code PAID}. Every provider — Stripe
 * webhook, Interac manual confirmation, Mock — funnels its confirmation signal through here so the
 * state machine and fulfillment live in one spot. The transition is idempotent: redelivered
 * webhooks or double-clicks are safe.
 */
@Service
@AllArgsConstructor
public class PaymentConfirmationService {

    private final OrderRepository orderRepository;
    private final FulfillmentService fulfillmentService;

    @Transactional
    public Order confirmPayment(UUID orderId, String providerRef) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.PAID) {
            return order; // idempotent: already confirmed
        }
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ConflictException(
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
}
