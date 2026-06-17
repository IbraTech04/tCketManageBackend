package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a paid order into issued tickets — one {@code ACTIVE} {@link Ticket} per {@link OrderItem}
 * seat. Delivery is decoupled from issuance: tickets are persisted in this transaction, and a
 * {@link TicketsIssuedEvent} is published so emails are sent asynchronously <em>after commit</em>.
 * This keeps SMTP round-trips out of the order-confirmation transaction entirely.
 */
@Service
@AllArgsConstructor
public class FulfillmentService {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void fulfill(Order order) {
        List<UUID> issuedTicketIds = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Ticket ticket = Ticket.builder()
                    .ID(UUID.randomUUID())
                    .firstName(item.getAttendeeFirstName())
                    .lastName(item.getAttendeeLastName())
                    .email(item.getAttendeeEmail())
                    .event(order.getEvent())
                    .ticketType(item.getTicketType())
                    .status(TicketStatus.ACTIVE)
                    .order(order)
                    .build();
            ticketRepository.save(ticket);
            issuedTicketIds.add(ticket.getID());
        }
        eventPublisher.publishEvent(new TicketsIssuedEvent(issuedTicketIds));
    }
}
