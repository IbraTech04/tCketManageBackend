package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.security.AdminGuard;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Raw payment-provider callbacks. Automatic providers POST to the generic webhook; the Mock manual
 * path is settled via the operator endpoint. Order-level actions (manual confirm) live on
 * {@code OrderController}.
 */
@RestController
@RequestMapping("/api/v1/payments")
@AllArgsConstructor
public class PaymentWebhookController {

    private final PaymentConfirmationService confirmationService;
    private final AdminGuard adminGuard;

    /**
     * Generic webhook for automatic providers. The real Stripe implementation will verify the
     * signature, parse the event, look up the order by provider ref, and confirm. Not yet wired.
     */
    @PostMapping("/{providerId}/webhook")
    public ResponseEntity<String> webhook(@PathVariable String providerId,
                                          @RequestBody(required = false) String payload) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("Webhook handling for provider '" + providerId + "' is not yet implemented.");
    }

    /**
     * Test hook to settle a Mock order created in manual mode.
     */
    @PostMapping("/mock/{orderId}/complete")
    public OrderResponse completeMockPayment(
            @PathVariable UUID orderId,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        adminGuard.require(adminToken);
        return OrderResponse.from(confirmationService.confirmPayment(orderId, null));
    }
}
