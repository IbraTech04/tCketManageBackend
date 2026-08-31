package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.PaymentMatchSuggestion;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.repository.EtransferReceiptRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The review queue for e-Transfers that arrived but were never tied to an order.
 *
 * <p>This is the one quarantine path that used to flag nothing. When the memo carries no usable code,
 * no order is found, so nothing is set to {@code QUARANTINED} and nothing appears anywhere an operator
 * looks — meanwhile the buyer's order sits in {@code AWAITING_PAYMENT} until the expiry sweep releases
 * their seats and the tickets go back on sale. They paid. This surfaces those payments while the seats
 * can still be recovered.
 *
 * <p>Suggestions are ranked, never applied. {@link EtransferConfirmationService} never auto-confirms on
 * a partial match, and every signal available here is partial: buyers of the same tier all pay the
 * identical amount, and a bank's name for an account routinely isn't the buyer's name on the order.
 * A human picks, and {@link #link} is the only thing that settles anything.
 */
@Service
@AllArgsConstructor
public class UnmatchedPaymentService {

    private static final Logger log = LoggerFactory.getLogger(UnmatchedPaymentService.class);

    /**
     * Orders worth offering as candidates: everything that has not already taken someone's money.
     *
     * <p>{@code EXPIRED} and {@code CANCELLED} are included in case an order has expired before
     * the operator is able to match it.
     */
    private static final Set<OrderStatus> MATCHABLE = EnumSet.of(
            OrderStatus.AWAITING_PAYMENT, OrderStatus.QUARANTINED,
            OrderStatus.EXPIRED, OrderStatus.CANCELLED);

    private final EtransferReceiptRepository receiptRepository;
    private final OrderRepository orderRepository;
    private final PaymentConfirmationService paymentConfirmationService;
    private final ReferenceCodeMatcher matcher;

    /** Every payment still awaiting a decision, newest first. */
    @Transactional(readOnly = true)
    public List<EtransferReceipt> queue() {
        return receiptRepository.findByOrderIsNullAndDismissedAtIsNullOrderByEmailReceivedAtDesc();
    }

    @Transactional(readOnly = true)
    public EtransferReceipt require(UUID receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found: " + receiptId));
    }

    /**
     * Orders this payment might belong to, best first.
     * See {@link com.ibrasoft.tcketmanagebackend.payment.etransfer.ReferenceCodeMatcher}
     */
    @Transactional(readOnly = true)
    public List<PaymentMatchSuggestion> suggestionsFor(EtransferReceipt receipt) {
        String memo = memoHaystack(receipt);
        return orderRepository.findByStatusInWithItems(MATCHABLE).stream()
                .map(order -> score(receipt, order, memo))
                .filter(s -> s.getCodeDistance() != ReferenceCodeMatcher.NO_MATCH)
                .sorted(Comparator
                        .comparingInt(PaymentMatchSuggestion::getCodeDistance)
                        .thenComparing(PaymentMatchSuggestion::isAmountMatches, Comparator.reverseOrder())
                        .thenComparing(PaymentMatchSuggestion::isWithinHoldWindow, Comparator.reverseOrder())
                        .thenComparing(PaymentMatchSuggestion::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * Everything on the receipt a mistyped code could be hiding in.
     *
     * <p>Normally the memo, but a code mangled badly enough that the parser rejected its shape leaves
     * {@code referenceCode} null while the raw text survives in the memo. Concatenating both costs
     * nothing — the matcher slides a window over the result — and covers the case where the parser did
     * recover a well-formed code that simply matches no order.
     */
    private static String memoHaystack(EtransferReceipt receipt) {
        String memo = receipt.getMemo() == null ? "" : receipt.getMemo();
        String code = receipt.getReferenceCode() == null ? "" : receipt.getReferenceCode();
        return code.isEmpty() ? memo : memo + " " + code;
    }

    private PaymentMatchSuggestion score(EtransferReceipt receipt, Order order, String memo) {
        boolean amountMatches = receipt.getAmount() != null
                && order.getAmountTotal() != null
                && receipt.getAmount().compareTo(order.getAmountTotal()) == 0
                && (receipt.getCurrency() == null || receipt.getCurrency().equalsIgnoreCase(order.getCurrency()));

        return PaymentMatchSuggestion.builder()
                .orderId(order.getId())
                .referenceCode(order.getReferenceCode())
                .buyerEmail(order.getBuyerEmail())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .amountTotal(order.getAmountTotal())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .codeDistance(matcher.distance(memo, order.getReferenceCode()))
                .amountMatches(amountMatches)
                .withinHoldWindow(withinHoldWindow(receipt.getEmailReceivedAt(), order))
                .build();
    }

    /** Whether the payment landed between the order being placed and its hold lapsing. */
    private static boolean withinHoldWindow(Instant receivedAt, Order order) {
        if (receivedAt == null || order.getCreatedAt() == null) {
            return false;
        }
        if (receivedAt.isBefore(order.getCreatedAt())) {
            return false; // paid before the order existed: not this one
        }
        return order.getExpiresAt() == null || !receivedAt.isAfter(order.getExpiresAt());
    }

    /**
     * Attaches the payment to an order and settles it
     */
    @Transactional
    public Order link(UUID receiptId, UUID orderId) {
        EtransferReceipt receipt = require(receiptId);
        if (receipt.getOrder() != null) {
            throw new ConflictException("Payment " + receiptId + " is already linked to order "
                    + receipt.getOrder().getId());
        }
        if (receipt.getDismissedAt() != null) {
            throw new ConflictException("Payment " + receiptId + " was dismissed; undismiss it to link it");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        receipt.setOrder(order);
        receiptRepository.save(receipt);

        log.info("Operator linked e-Transfer receipt {} (interac ref {}, {} {}) to order {} ({})",
                receiptId, receipt.getInteracReference(), receipt.getAmount(), receipt.getCurrency(),
                orderId, order.getReferenceCode());

        return paymentConfirmationService.confirmPayment(orderId, receipt.getInteracReference());
    }

    /**
     * Writes a payment off as never going to match: sent to the wrong organisation, or a duplicate
     * that is owed no tickets. Idempotent, so a double-click doesn't overwrite who first dismissed it.
     */
    @Transactional
    public EtransferReceipt dismiss(UUID receiptId, String dismissedBy, String note) {
        EtransferReceipt receipt = require(receiptId);
        if (receipt.getOrder() != null) {
            throw new ConflictException("Payment " + receiptId + " is linked to an order and cannot be dismissed");
        }
        if (receipt.getDismissedAt() != null) {
            return receipt;
        }
        receipt.setDismissedAt(Instant.now());
        receipt.setDismissedBy(truncate(dismissedBy, 255));
        receipt.setDismissalNote(truncate(note, 500));
        log.info("Operator {} dismissed unmatched e-Transfer receipt {} ({} {}): {}",
                dismissedBy, receiptId, receipt.getAmount(), receipt.getCurrency(), note);
        return receiptRepository.save(receipt);
    }

    /** Puts a dismissed payment back in the queue, for when the write-off was premature. */
    @Transactional
    public EtransferReceipt restore(UUID receiptId) {
        EtransferReceipt receipt = require(receiptId);
        receipt.setDismissedAt(null);
        receipt.setDismissedBy(null);
        receipt.setDismissalNote(null);
        return receiptRepository.save(receipt);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
