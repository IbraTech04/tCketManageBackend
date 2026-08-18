package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.service.email.EmailDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

    @Mock private EmailDispatchService emailDispatchService;

    @InjectMocks
    private OrderNotificationListener listener;

    @Test
    void handOffCarriesBothTheOrderAndTheNoticeKind() {
        UUID orderId = UUID.randomUUID();

        listener.onOrderNotification(
                new OrderNotificationEvent(orderId, OrderNotification.QUARANTINED));

        verify(emailDispatchService, times(1))
                .sendOrderNotificationInBackground(orderId, OrderNotification.QUARANTINED);
    }

    /**
     * The phase is the whole point of this listener: on {@code AFTER_COMMIT} a transition that rolls
     * back sends nothing, whereas the default {@code AFTER_COMPLETION} would tell a buyer their
     * order expired even when the expiry failed and the order still stands.
     */
    @Test
    void listensAfterCommitOnly() throws NoSuchMethodException {
        Method handler = OrderNotificationListener.class
                .getMethod("onOrderNotification", OrderNotificationEvent.class);

        TransactionalEventListener annotation = handler.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation, "notifications must be bound to the transaction, not the publish");
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
