package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.DismissPaymentRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.LinkPaymentRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.OrderResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.PaymentMatchSuggestion;
import com.ibrasoft.tcketmanagebackend.model.dto.response.UnmatchedPaymentResponse;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.payment.etransfer.EtransferReceiptLookup;
import com.ibrasoft.tcketmanagebackend.payment.etransfer.UnmatchedPaymentService;
import com.ibrasoft.tcketmanagebackend.service.order.OrderOwnerResolver;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Operator review queue for e-Transfers that arrived but matched no order.
 *
 * <p>Every route is behind the same admin role as quarantine approval, and for the same reason:
 * linking a payment settles an order and issues tickets, so it is not something an event manager
 * should be able to do by guessing a receipt id.
 */
@RestController
@RequestMapping("${tcketmanage.base-path:/tcket}/payments/unmatched")
@PreAuthorize("hasRole(@tcketmanageRoles.admin)")
@AllArgsConstructor
public class UnmatchedPaymentController {

    private final UnmatchedPaymentService unmatchedPayments;
    private final EtransferReceiptLookup receiptLookup;
    private final OrderOwnerResolver ownerResolver;

    /** Payments still awaiting a decision, newest first. */
    @GetMapping
    public List<UnmatchedPaymentResponse> list() {
        return unmatchedPayments.queue().stream()
                .map(UnmatchedPaymentResponse::from)
                .toList();
    }

    /**
     * Orders this payment might belong to, best first.
     */
    @GetMapping("/{id}/suggestions")
    public List<PaymentMatchSuggestion> suggestions(@PathVariable UUID id) {
        return unmatchedPayments.suggestionsFor(unmatchedPayments.require(id));
    }

    /**
     * Attaches the payment to the order an operator chose and settles it.
     */
    @PostMapping("/{id}/link")
    public OrderResponse link(@PathVariable UUID id, @Valid @RequestBody LinkPaymentRequest request) {
        Order order = unmatchedPayments.link(id, request.getOrderId());
        return OrderResponse.from(order, null, receiptLookup.latestFor(order.getId()));
    }

    /**
     * Writes the payment off as never going to match an order.
     */
    @PostMapping("/{id}/dismiss")
    public UnmatchedPaymentResponse dismiss(@PathVariable UUID id,
                                            @Valid @RequestBody(required = false) DismissPaymentRequest request) {
        String note = request != null ? request.getNote() : null;
        EtransferReceipt dismissed = unmatchedPayments.dismiss(id, ownerResolver.currentOwnerRef(), note);
        return UnmatchedPaymentResponse.from(dismissed);
    }

    /**
     * Puts a dismissed payment back in the queue, for when the write-off was premature.
     */
    @PostMapping("/{id}/restore")
    public UnmatchedPaymentResponse restore(@PathVariable UUID id) {
        return UnmatchedPaymentResponse.from(unmatchedPayments.restore(id));
    }
}
