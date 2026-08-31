package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default {@link EmailService} that logs instead of sending. Active unless {@code tcketmanage.email.enabled=true},
 * in which case the SMTP {@link SmtpEmailService} takes over.
 */
@Service
@ConditionalOnProperty(prefix = "tcketmanage.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public boolean sendTicket(Ticket ticket) {
        log.info("[email stub] Would send ticket {} to {}", ticket.getID(), ticket.getEmail());
        // Report success so delivery flows (and lastTicketSent stamping) work end-to-end in
        // deployments where email is intentionally disabled.
        return true;
    }

    @Override
    public boolean sendOrderNotification(Order order, OrderNotification notification) {
        log.info("[email stub] Would send {} notification for order {} ({}) to {}",
                notification, order.getId(), order.getReferenceCode(), order.getBuyerEmail());
        return true;
    }
}
