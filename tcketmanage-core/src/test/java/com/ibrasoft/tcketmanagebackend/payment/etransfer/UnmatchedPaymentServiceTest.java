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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnmatchedPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T02:35:00Z");

    @Mock
    private EtransferReceiptRepository receiptRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    private UnmatchedPaymentService service;

    @BeforeEach
    void setUp() {
        // Real matcher: the ranking is the feature, so mocking it would test nothing.
        service = new UnmatchedPaymentService(
                receiptRepository, orderRepository, paymentConfirmationService, new ReferenceCodeMatcher());
    }

    private static EtransferReceipt receipt(String memo, String amount) {
        return EtransferReceipt.builder()
                .id(UUID.randomUUID())
                .memo(memo)
                .amount(new BigDecimal(amount))
                .currency("CAD")
                .interacReference("INTREF1")
                .emailReceivedAt(NOW)
                .build();
    }

    private static Order order(String code, String amount, OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .referenceCode(code)
                .amountTotal(new BigDecimal(amount))
                .currency("CAD")
                .status(status)
                .createdAt(NOW.minus(1, ChronoUnit.HOURS))
                .expiresAt(NOW.plus(47, ChronoUnit.HOURS))
                .build();
    }

    // --- suggestions ---

    @Test
    void mistypedCode_rankedAboveUnrelatedOrders() {
        Order intended = order("ABCD-EFGH", "35.00", OrderStatus.AWAITING_PAYMENT);
        Order other = order("WXYZ-2345", "35.00", OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByStatusInWithItems(any())).thenReturn(List.of(other, intended));

        List<PaymentMatchSuggestion> suggestions =
                service.suggestionsFor(receipt("payment ABCD-EFGX", "35.00"));

        assertFalse(suggestions.isEmpty());
        assertEquals(intended.getId(), suggestions.get(0).getOrderId());
        assertEquals(1, suggestions.get(0).getCodeDistance());
        // The unrelated code is nowhere near, so it isn't offered at all.
        assertEquals(1, suggestions.size());
    }

    @Test
    void reportsTheEvidenceBehindEachSuggestion() {
        Order o = order("ABCD-EFGH", "35.00", OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByStatusInWithItems(any())).thenReturn(List.of(o));

        PaymentMatchSuggestion s = service.suggestionsFor(receipt("ABCD-EFGX", "35.00")).get(0);

        assertEquals(1, s.getCodeDistance());
        // The excerpt the UI highlights comes from the matcher, not a client re-derivation.
        assertEquals("ABCDEFGX", s.getMemoExcerpt());
        assertTrue(s.isAmountMatches());
        assertTrue(s.isWithinHoldWindow());
        assertEquals("AWAITING_PAYMENT", s.getStatus());
    }

    @Test
    void amountMismatchIsSurfacedNotHidden() {
        // The operator is told the amount disagrees rather than having the candidate filtered away —
        // a buyer who underpaid still needs their payment attached to something.
        Order o = order("ABCD-EFGH", "35.00", OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByStatusInWithItems(any())).thenReturn(List.of(o));

        PaymentMatchSuggestion s = service.suggestionsFor(receipt("ABCD-EFGH", "5.00")).get(0);

        assertEquals(0, s.getCodeDistance());
        assertFalse(s.isAmountMatches());
    }

    @Test
    void paymentBeforeTheOrderExisted_isOutsideTheHoldWindow() {
        Order o = order("ABCD-EFGH", "35.00", OrderStatus.AWAITING_PAYMENT);
        o.setCreatedAt(NOW.plus(1, ChronoUnit.HOURS));
        when(orderRepository.findByStatusInWithItems(any())).thenReturn(List.of(o));

        assertFalse(service.suggestionsFor(receipt("ABCD-EFGH", "35.00")).get(0).isWithinHoldWindow());
    }

    @Test
    void noPlausibleOrder_returnsEmptyRatherThanWeakGuesses() {
        // An empty list is a useful answer: this payment probably isn't ours.
        when(orderRepository.findByStatusInWithItems(any()))
                .thenReturn(List.of(order("WXYZ-2345", "35.00", OrderStatus.AWAITING_PAYMENT)));

        assertTrue(service.suggestionsFor(receipt("thanks for the tickets!", "35.00")).isEmpty());
    }

    @Test
    void candidateSetExcludesSettledOrders() {
        when(orderRepository.findByStatusInWithItems(any())).thenReturn(List.of());

        service.suggestionsFor(receipt("ABCD-EFGH", "35.00"));

        // A payment pointing at a PAID order is a duplicate to refund, not a match to make.
        verify(orderRepository).findByStatusInWithItems(argThat(statuses ->
                statuses.contains(OrderStatus.AWAITING_PAYMENT)
                        && statuses.contains(OrderStatus.EXPIRED)
                        && statuses.contains(OrderStatus.CANCELLED)
                        && !statuses.contains(OrderStatus.PAID)
                        && !statuses.contains(OrderStatus.REFUNDED)));
    }

    // --- linking ---

    @Test
    void link_attachesReceiptThenSettlesThroughConfirmPayment() {
        // Routing through confirmPayment is what makes the expired case correct: it re-acquires the
        // seats or falls to REFUND_PENDING. A direct setStatus(PAID) would issue tickets for seats
        // someone else has since bought.
        EtransferReceipt r = receipt("ABCD-EFGX", "35.00");
        Order o = order("ABCD-EFGH", "35.00", OrderStatus.EXPIRED);
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));
        when(receiptRepository.save(any(EtransferReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        service.link(r.getId(), o.getId());

        assertEquals(o, r.getOrder());
        verify(receiptRepository).save(r);
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void link_alreadyLinkedReceipt_isRejected() {
        EtransferReceipt r = receipt("ABCD-EFGX", "35.00");
        r.setOrder(order("ABCD-EFGH", "35.00", OrderStatus.PAID));
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThrows(ConflictException.class, () -> service.link(r.getId(), UUID.randomUUID()));
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void link_dismissedReceipt_isRejected() {
        EtransferReceipt r = receipt("ABCD-EFGX", "35.00");
        r.setDismissedAt(NOW);
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThrows(ConflictException.class, () -> service.link(r.getId(), UUID.randomUUID()));
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void link_unknownOrder_throwsWithoutTouchingTheReceipt() {
        EtransferReceipt r = receipt("ABCD-EFGX", "35.00");
        UUID missing = UUID.randomUUID();
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(orderRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.link(r.getId(), missing));
        assertNull(r.getOrder());
        verify(receiptRepository, never()).save(any());
    }

    // --- dismissal ---

    @Test
    void dismiss_recordsWhoAndWhy() {
        EtransferReceipt r = receipt("wrong org", "35.00");
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(receiptRepository.save(any(EtransferReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        EtransferReceipt dismissed = service.dismiss(r.getId(), "admin@example.com", "  sent to us by mistake  ");

        assertNotNull(dismissed.getDismissedAt());
        assertEquals("admin@example.com", dismissed.getDismissedBy());
        assertEquals("sent to us by mistake", dismissed.getDismissalNote());
    }

    @Test
    void dismiss_isIdempotent() {
        // A double-click must not overwrite who first wrote it off.
        EtransferReceipt r = receipt("wrong org", "35.00");
        r.setDismissedAt(NOW.minus(1, ChronoUnit.DAYS));
        r.setDismissedBy("first@example.com");
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));

        EtransferReceipt result = service.dismiss(r.getId(), "second@example.com", "again");

        assertEquals("first@example.com", result.getDismissedBy());
        assertEquals(NOW.minus(1, ChronoUnit.DAYS), result.getDismissedAt());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void dismiss_linkedReceipt_isRejected() {
        EtransferReceipt r = receipt("ABCD-EFGH", "35.00");
        r.setOrder(order("ABCD-EFGH", "35.00", OrderStatus.PAID));
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThrows(ConflictException.class, () -> service.dismiss(r.getId(), "admin", null));
    }

    @Test
    void restore_putsItBackInTheQueue() {
        EtransferReceipt r = receipt("wrong org", "35.00");
        r.setDismissedAt(NOW);
        r.setDismissedBy("admin");
        r.setDismissalNote("premature");
        when(receiptRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(receiptRepository.save(any(EtransferReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        EtransferReceipt restored = service.restore(r.getId());

        assertNull(restored.getDismissedAt());
        assertNull(restored.getDismissedBy());
        assertNull(restored.getDismissalNote());
    }
}
