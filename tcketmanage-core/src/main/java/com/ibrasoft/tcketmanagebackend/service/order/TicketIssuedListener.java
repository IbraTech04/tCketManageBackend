package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.service.email.EmailDispatchService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Dispatches ticket emails once the order that issued them has committed. The listener runs
 * synchronously after commit but immediately hands each ticket to {@link EmailDispatchService}, whose
 * {@code @Async} send moves the actual SMTP work onto the email pool — so the committing thread isn't
 * held either.
 */
@Component
@AllArgsConstructor
public class TicketIssuedListener {

    private final EmailDispatchService emailDispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketsIssued(TicketsIssuedEvent event) {
        for (UUID ticketId : event.ticketIds()) {
            emailDispatchService.sendInBackground(ticketId);
        }
    }
}
