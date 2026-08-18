package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.service.email.EmailDispatchService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the buyer's order notice once the transition that triggered it has committed — the
 * order-level counterpart to {@link TicketIssuedListener}. The listener runs synchronously after
 * commit but immediately hands off to {@link EmailDispatchService}, whose {@code @Async} send moves
 * the SMTP work onto the email pool, so neither the committing thread nor the expiry sweep is held.
 *
 * <p>Because the phase is {@code AFTER_COMMIT}, a rolled-back transition sends nothing: an expiry
 * that fails partway never tells the buyer their order expired when it in fact still stands.
 */
@Component
@AllArgsConstructor
public class OrderNotificationListener {

    private final EmailDispatchService emailDispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderNotification(OrderNotificationEvent event) {
        emailDispatchService.sendOrderNotificationInBackground(event.orderId(), event.notification());
    }
}
