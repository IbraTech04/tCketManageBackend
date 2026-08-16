package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The sweep itself only orchestrates: it queries candidates unlocked, then expires each one in its
 * own transaction via {@link OrderTransactions#expireIfStillAwaiting} (covered by
 * {@link OrderTransactionsTest}). These tests verify that per-candidate isolation.
 */
@ExtendWith(MockitoExtension.class)
class OrderExpiryServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderTransactions orderTransactions;

    @InjectMocks
    private OrderExpiryService expiryService;

    private Order candidate(String ref) {
        return Order.builder().id(UUID.randomUUID()).referenceCode(ref)
                .status(OrderStatus.AWAITING_PAYMENT).build();
    }

    @Test
    void sweep_expiresEachCandidateInItsOwnTransaction() {
        Order a = candidate("ORD-1");
        Order b = candidate("ORD-2");
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(a, b));
        when(orderTransactions.expireIfStillAwaiting(any())).thenReturn(true);

        expiryService.sweepExpiredOrders();

        verify(orderTransactions, times(1)).expireIfStillAwaiting(a.getId());
        verify(orderTransactions, times(1)).expireIfStillAwaiting(b.getId());
    }

    @Test
    void sweep_oneCandidateFails_othersStillProcessed() {
        Order failing = candidate("ORD-1");
        Order ok = candidate("ORD-2");
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of(failing, ok));
        when(orderTransactions.expireIfStillAwaiting(failing.getId()))
                .thenThrow(new RuntimeException("db hiccup"));
        when(orderTransactions.expireIfStillAwaiting(ok.getId())).thenReturn(true);

        expiryService.sweepExpiredOrders();

        verify(orderTransactions, times(1)).expireIfStillAwaiting(ok.getId());
    }

    @Test
    void sweep_noExpiredOrders_noop() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(Instant.class)))
                .thenReturn(List.of());

        expiryService.sweepExpiredOrders();

        verifyNoInteractions(orderTransactions);
    }
}
