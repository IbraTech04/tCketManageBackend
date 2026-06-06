package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default {@link EmailService} that logs instead of sending. When a real SMTP implementation is
 * added, mark it {@code @Primary} (or disable this one via configuration) so it takes precedence.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendTickets(Order order, List<Ticket> tickets) {
        log.info("[email stub] Would send {} ticket(s) for order {} to {}",
                tickets.size(), order.getId(), order.getBuyerEmail());
    }
}
