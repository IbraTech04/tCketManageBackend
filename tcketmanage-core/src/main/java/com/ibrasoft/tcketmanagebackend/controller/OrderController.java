package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.DenyQuarantineRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.RefundService;
import com.ibrasoft.tcketmanagebackend.service.order.OrderAccessPolicy;
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
@RequestMapping("${tcketmanage.base-path:/tcket}/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentConfirmationService confirmationService;
    private final OrderAccessPolicy accessPolicy;
    private final RefundService refundService;

    // Operator/support order book. Provide exactly one of eventId (all orders for an event) or
    // externalRef (all orders for a host-owned owner ref). A host's own "my orders for the logged-in
    // user" should NOT use this role-guarded endpoint; it should query OrderService/OrderRepository
    // directly with the authenticated user's ref.
    @PreAuthorize("@tcketmanageAuthz.canManageEvents()")
    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(required = false) UUID eventId,
                                         @RequestParam(required = false) String externalRef,
                                         @RequestParam(required = false) OrderStatus status) {
        if ((eventId == null) == (externalRef == null)) {
            throw new IllegalArgumentException("Provide exactly one of 'eventId' or 'externalRef'");
        }
        // `status` narrows the event order book (used by the quarantine/refund queues). It is not
        // offered on the externalRef path, which answers "this owner's orders" in full.
        List<Order> orders = eventId != null
                ? orderService.getOrdersByEvent(eventId, status)
                : orderService.getOrdersByExternalRef(externalRef);
        return orders.stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(result.order(), result.initiation()));
    }

    // SECURITY: no role guard — buyers self-serve, including guest checkout, so this must stay
    // reachable unauthenticated. Ownership is enforced per-entity instead: once the order is loaded,
    // OrderAccessPolicy checks its externalRef against the caller. An order with no owner (guest)
    // falls back to the capability-URL model, where possession of the unguessable UUID is the
    // permission. See OrderAccessPolicy for why this cannot be expressed as a URL-level rule.
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        Order order = orderService.getOrder(id);
        accessPolicy.requireAccess(order.getExternalRef(), "Order");
        return OrderResponse.from(order);
    }

    // SECURITY: see getOrder. Ownership is checked before cancelling, not after — a denied caller
    // must not be able to cancel someone else's order and only then be told they cannot read it.
    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        accessPolicy.requireAccess(orderService.getOrder(id).getExternalRef(), "Order");
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    /**
     * Operator confirmation that a manual payment (e.g. an Interac e-Transfer) was received.
     */
    @PreAuthorize("@tcketmanageAuthz.canAdminister()")
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
