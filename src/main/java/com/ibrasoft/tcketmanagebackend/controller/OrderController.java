package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.security.AdminGuard;
import com.ibrasoft.tcketmanagebackend.service.order.OrderCreationResult;
import com.ibrasoft.tcketmanagebackend.service.order.OrderService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentConfirmationService confirmationService;
    private final AdminGuard adminGuard;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(result.order(), result.initiation()));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancelOrder(id));
    }

    /**
     * Operator confirmation that a manual payment (e.g. an Interac e-Transfer) was received.
     */
    @PostMapping("/{id}/confirm-manual-payment")
    public OrderResponse confirmManualPayment(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        adminGuard.require(adminToken);
        return OrderResponse.from(confirmationService.confirmPayment(id, null));
    }
}
