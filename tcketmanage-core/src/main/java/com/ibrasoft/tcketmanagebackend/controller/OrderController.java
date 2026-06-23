package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
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
@RequestMapping("/tcket/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentConfirmationService confirmationService;

    // Operator/support order book. Provide exactly one of eventId (all orders for an event) or
    // externalRef (all orders for a host-owned owner ref). A host's own "my orders for the logged-in
    // user" should NOT use this role-guarded endpoint; it should query OrderService/OrderRepository
    // directly with the authenticated user's ref.
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(required = false) UUID eventId,
                                         @RequestParam(required = false) String externalRef) {
        if ((eventId == null) == (externalRef == null)) {
            throw new IllegalArgumentException("Provide exactly one of 'eventId' or 'externalRef'");
        }
        List<Order> orders = eventId != null
                ? orderService.getOrdersByEvent(eventId)
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
}
