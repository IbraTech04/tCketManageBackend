package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.DenyQuarantineRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.RefundService;
import com.ibrasoft.tcketmanagebackend.service.order.OrderCreationResult;
import com.ibrasoft.tcketmanagebackend.service.order.OrderService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentConfirmationService confirmationService;
    private final RefundService refundService;

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam UUID eventId,
                                         @RequestParam(required = false) OrderStatus status) {
        return orderService.getOrdersByEvent(eventId, status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(result.order(), result.initiation()));
    }

    // SECURITY (capability-URL): the unguessable order UUID authorizes retrieval - holding it is the
    // permission. Left intentionally open (no role): buyers self-serve, including guest checkout.
    // Core has no user model, so it cannot verify the caller *owns* this order; ownership is the
    // embedding host's concern (it knows the authenticated buyer). A host that needs real ownership
    // enforcement should gate via a host-provided check (optional access-verifier bean / @PostAuthorize),
    // NOT a role guard here — a role would break self-service and guest checkout.
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    // SECURITY (capability-URL): see getOrder — possession of the order UUID authorizes cancellation.
    // Intentionally open for buyer self-service; ownership is the host's concern, not core's.
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    /**
     * Operator confirmation that a manual payment (e.g. an Interac e-Transfer) was received.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/confirm-manual-payment")
    public OrderResponse confirmManualPayment(@PathVariable UUID id) {
        return OrderResponse.from(confirmationService.confirmPayment(id, null));
    }

    /**
     * Operator approval of a quarantined payment: the held seats are fulfilled and the order settles
     * to {@code PAID}. Kept distinct from {@code confirm-manual-payment} so a mismatched-amount order
     * can't be cleared by the normal confirm button — approving a quarantine is a deliberate act.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/quarantine/approve")
    public OrderResponse approveQuarantine(@PathVariable UUID id) {
        return OrderResponse.from(confirmationService.approveQuarantine(id));
    }

    /**
     * Operator denial of a quarantined payment: releases the held seats and either cancels the order
     * or queues it for refund, per {@link DenyQuarantineRequest#isFundsReceived()}.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/quarantine/deny")
    public OrderResponse denyQuarantine(@PathVariable UUID id,
                                        @RequestBody(required = false) DenyQuarantineRequest request) {
        boolean fundsReceived = request != null && request.isFundsReceived();
        return OrderResponse.from(confirmationService.denyQuarantine(id, fundsReceived));
    }

    /**
     * Refunds a paid order: voids its tickets, releases their seats, and triggers the provider refund.
     * Settles to {@code REFUNDED} (automatic provider) or {@code REFUND_PENDING} (manual payout).
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/refund")
    public OrderResponse refundOrder(@PathVariable UUID id) {
        return OrderResponse.from(refundService.refundOrder(id));
    }

    /**
     * Marks a {@code REFUND_PENDING} order as {@code REFUNDED} once the operator has paid the manual
     * refund out of band.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/refund/complete")
    public OrderResponse completeRefund(@PathVariable UUID id) {
        return OrderResponse.from(refundService.markRefundComplete(id));
    }
}
