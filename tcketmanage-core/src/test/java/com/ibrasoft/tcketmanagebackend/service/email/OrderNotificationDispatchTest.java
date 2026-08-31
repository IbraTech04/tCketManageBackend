package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the order-notification half of {@link EmailDispatchService}: it loads the order through the
 * transactional {@link EmailTransactions} (never the repository directly, so the lazy items are
 * initialized), and tolerates an order that has been deleted between the transition committing and
 * this async send running.
 */
@ExtendWith(MockitoExtension.class)
class OrderNotificationDispatchTest {

    @Mock private EmailTransactions emailTransactions;
    @Mock private EmailService emailService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private TcketManageProperties properties;

    @InjectMocks
    private EmailDispatchService dispatchService;

    private Order expiredOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.EXPIRED)
                .referenceCode("ORD-TEST")
                .buyerEmail("buyer@example.com")
                .build();
    }

    @Test
    void sendOrderNotification_loadsOrderAndSendsRequestedNotice() {
        Order order = expiredOrder();
        when(emailTransactions.loadOrder(order.getId())).thenReturn(Optional.of(order));

        dispatchService.sendOrderNotificationInBackground(order.getId(), OrderNotification.EXPIRED);

        verify(emailService, times(1)).sendOrderNotification(order, OrderNotification.EXPIRED);
    }

    @Test
    void sendOrderNotification_orderGone_isSkippedNotThrown() {
        // The async send runs after commit, so the order can be deleted out from under it. An
        // exception here would surface on the email pool with nothing to catch it.
        UUID id = UUID.randomUUID();
        when(emailTransactions.loadOrder(id)).thenReturn(Optional.empty());

        dispatchService.sendOrderNotificationInBackground(id, OrderNotification.CANCELLED);

        verifyNoInteractions(emailService);
    }

    @Test
    void sendOrderNotification_neverTouchesTheTicketPath() {
        Order order = expiredOrder();
        when(emailTransactions.loadOrder(order.getId())).thenReturn(Optional.of(order));

        dispatchService.sendOrderNotificationInBackground(
                order.getId(), OrderNotification.REFUND_PENDING);

        verify(emailTransactions, never()).loadTicket(any());
        verify(emailTransactions, never()).markTicketSent(any());
        verify(emailService, never()).sendTicket(any());
    }
}
