package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobStatus;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs email delivery on the {@code emailExecutor} pool, off the request/fulfillment/sweep threads.
 * Three entry points:
 *
 * <ul>
 *   <li>{@link #sendInBackground(UUID)} — fire-and-forget single ticket send (order fulfillment,
 *       comp tickets), no progress tracking.</li>
 *   <li>{@link #sendOrderNotificationInBackground(UUID, OrderNotification)} — fire-and-forget
 *       order-level notice for the states that issue no tickets (expiry, cancellation, quarantine,
 *       refund pending).</li>
 *   <li>{@link #runJob(EmailJobStatus, List)} — tracked batch that streams progress to
 *       {@code /topic/email-jobs/{jobId}} and updates the {@link EmailJobStatus} as it goes.</li>
 * </ul>
 *
 * <p>A job is processed sequentially on a single worker (its progress counters are advanced in
 * order, and we avoid hammering the SMTP provider with a burst); separate jobs and fire-and-forget
 * sends still run concurrently up to the pool size.
 */
@Service
@AllArgsConstructor
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final EmailTransactions emailTransactions;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TcketManageProperties properties;

    /** Fire-and-forget delivery of a single ticket. Failures are logged, never thrown. */
    @Async("emailExecutor")
    public void sendInBackground(UUID ticketId) {
        deliver(ticketId);
    }

    /**
     * Fire-and-forget delivery of an order-level notice (expiry, cancellation, quarantine, refund
     * pending). Nothing is tracked or retried: the order has already reached its new state, and a
     * failed courtesy email must not be able to disturb that.
     */
    @Async("emailExecutor")
    public void sendOrderNotificationInBackground(UUID orderId, OrderNotification notification) {
        Optional<Order> loaded = emailTransactions.loadOrder(orderId);
        if (loaded.isEmpty()) {
            log.warn("Skipping {} notification for order {}: no longer exists", notification, orderId);
            return;
        }
        emailService.sendOrderNotification(loaded.get(), notification);
    }

    /** Runs a tracked batch: STARTED snapshot, a snapshot per ticket, then a terminal COMPLETED. */
    @Async("emailExecutor")
    public void runJob(EmailJobStatus status, List<UUID> ticketIds) {
        publish(status);
        for (UUID ticketId : ticketIds) {
            Delivery outcome = deliver(ticketId);
            status.record(outcome.email(), outcome.success());
            publish(status);
        }
        status.complete();
        publish(status);
        log.info("Email job {} ({}) finished: {} sent, {} failed of {}",
                status.getJobId(), status.getType(), status.getSent(), status.getFailed(), status.getTotal());
    }

    /**
     * Loads, renders and sends one ticket, stamping delivery on success. Returns the recipient (for
     * progress display) alongside the outcome.
     */
    private Delivery deliver(UUID ticketId) {
        Optional<Ticket> loaded = emailTransactions.loadTicket(ticketId);
        if (loaded.isEmpty()) {
            log.warn("Skipping email for ticket {}: no longer exists", ticketId);
            return new Delivery(null, false);
        }
        Ticket ticket = loaded.get();
        boolean sent = emailService.sendTicket(ticket);
        if (sent) {
            emailTransactions.markTicketSent(ticketId);
        }
        return new Delivery(ticket.getEmail(), sent);
    }

    /**
     * Pushes a snapshot to the job's STOMP destination. The prefix is configuration rather than a
     * constant because core no longer owns the message broker; see
     *  {@code WebSocketDestinationRequirement},
     * which verifies at startup that the broker in force actually serves it.
     */
    private void publish(EmailJobStatus status) {
        messagingTemplate.convertAndSend(
                properties.getWebsocket().emailJobDestination(status.getJobId()), status);
    }

    private record Delivery(String email, boolean success) {}
}
