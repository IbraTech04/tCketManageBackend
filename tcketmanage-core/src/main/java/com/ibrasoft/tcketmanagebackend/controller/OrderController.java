package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.DenyQuarantineRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.PaymentReferenceRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.RefundService;
import com.ibrasoft.tcketmanagebackend.payment.etransfer.EtransferReceiptLookup;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("${tcketmanage.base-path:/tcket}/orders")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentConfirmationService confirmationService;
    private final OrderAccessPolicy accessPolicy;
    private final RefundService refundService;
    private final EtransferReceiptLookup receiptLookup;

    // Operator/support order book. Provide exactly one of eventId (all orders for an event),
    // externalRef (all orders for a host-owned owner ref), or referenceCode (the single order
    // bearing that XXXX-XXXX code). A host's own "my orders for the logged-in user" should NOT use
    // this role-guarded endpoint; it should query OrderService/OrderRepository directly with the
    // authenticated user's ref.
    //
    // referenceCode backs resolving an unmatched payment by hand: when the memo gave the matcher
    // nothing to work with, an operator identifies the order themselves and names it by the code they
    // can see in this order book. It returns a list of zero or one so a miss is an empty result the
    // caller can report as "no such code", rather than a 404 they have to special-case.
    @PreAuthorize("@tcketmanageAuthz.canManageEvents()")
    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(required = false) UUID eventId,
                                         @RequestParam(required = false) String externalRef,
                                         @RequestParam(required = false) String referenceCode,
                                         @RequestParam(required = false) OrderStatus status) {
        long provided = Stream.of(eventId, externalRef, referenceCode).filter(Objects::nonNull).count();
        if (provided != 1) {
            throw new IllegalArgumentException(
                    "Provide exactly one of 'eventId', 'externalRef' or 'referenceCode'");
        }
        // `status` narrows the event order book (used by the quarantine/refund queues). It is not
        // offered on the other paths, which answer a specific question in full.
        List<Order> orders;
        if (eventId != null) {
            orders = orderService.getOrdersByEvent(eventId, status);
        } else if (externalRef != null) {
            orders = orderService.getOrdersByExternalRef(externalRef);
        } else {
            orders = orderService.findByReferenceCode(referenceCode).map(List::of).orElseGet(List::of);
        }
        // One query for the whole page's receipts, not one per order.
        Map<UUID, EtransferReceipt> receipts = receiptLookup.latestByOrderId(
                orders.stream().map(Order::getId).toList());
        return orders.stream()
                .map(order -> OrderResponse.from(order, null, receipts.get(order.getId())))
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
        return OrderResponse.from(order, null, receiptLookup.latestFor(id));
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
     *
     * <p>The body is optional, so a bare POST still confirms without recording a reference. Supplying
     * one settles the order and records the reference in a single call: the operator has the
     * notification in front of them and can read the reference number straight off it.
     *
     * <p>This is the {@code AWAITING_PAYMENT} path only. A quarantined order is resolved through
     * {@link #approveQuarantine}, deliberately kept separate so the check that held the order cannot
     * be cleared from the routine confirm button.
     *
     * <p>Deliberately not {@code @Valid}: "no reference" is a legitimate confirmation, and callers
     * express it both by omitting the body and by sending an empty one. Validating the shared DTO
     * here would reject {@code {}} on its {@code @NotBlank}, which would break every existing caller
     * that posts an empty object. A blank reference is normalized to none instead.
     */
    @PreAuthorize("@tcketmanageAuthz.canAdminister()")
    @PostMapping("/{id}/confirm-manual-payment")
    public OrderResponse confirmManualPayment(@PathVariable UUID id,
                                              @RequestBody(required = false) PaymentReferenceRequest request) {
        String submitted = request != null ? request.getProviderRef() : null;
        String providerRef = (submitted == null || submitted.isBlank()) ? null : submitted.trim();
        return OrderResponse.from(confirmationService.confirmPayment(id, providerRef),
                null, receiptLookup.latestFor(id));
    }

    /**
     * Corrects the provider-side payment reference on an order without touching its status.
     *
     * <p>For the e-Transfer that never got matched automatically - the buyer mistyped the memo code,
     * omitted it, or the mailbox listener was down - so an operator can attach the real Interac
     * reference number after the fact. Separate from confirmation because an already-paid order must
     * be correctable without re-running fulfillment.
     *
     * <p>PUT rather than PATCH: the reference is the whole of this sub-resource, so replacing it is a
     * PUT, and {@code WebConfig}'s CORS policy does not list PATCH - a PATCH here would die at the
     * browser preflight rather than at anything a caller could see.
     */
    @PreAuthorize("@tcketmanageAuthz.canAdminister()")
    @PutMapping("/{id}/payment-reference")
    public OrderResponse updatePaymentReference(@PathVariable UUID id,
                                                @Valid @RequestBody PaymentReferenceRequest request) {
        return OrderResponse.from(confirmationService.updatePaymentReference(id, request.getProviderRef()),
                null, receiptLookup.latestFor(id));
    }

    /**
     * Operator approval of a quarantined payment: the held seats are fulfilled and the order settles
     * to {@code PAID}. Kept distinct from {@code confirm-manual-payment} so a mismatched-amount order
     * can't be cleared by the normal confirm button — approving a quarantine is a deliberate act.
     *
     * <p>Takes the same optional reference body as the manual-confirm endpoint, and for the same
     * reason it is not {@code @Valid}: approving without a reference stays legal, and an empty body
     * means exactly that. A quarantined e-Transfer is usually one the listener could not tie to the
     * order, so the approving operator is the one person holding its reference number.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @PostMapping("/{id}/quarantine/approve")
    public OrderResponse approveQuarantine(@PathVariable UUID id,
                                           @RequestBody(required = false) PaymentReferenceRequest request) {
        String submitted = request != null ? request.getProviderRef() : null;
        String providerRef = (submitted == null || submitted.isBlank()) ? null : submitted.trim();
        return OrderResponse.from(confirmationService.approveQuarantine(id, providerRef),
                null, receiptLookup.latestFor(id));
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
