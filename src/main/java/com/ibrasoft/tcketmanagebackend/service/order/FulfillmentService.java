package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.service.TicketDeliveryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns a paid order into issued tickets — one {@code ACTIVE} {@link Ticket} per {@link OrderItem}
 * seat — then hands each to {@link TicketDeliveryService} for delivery (which also stamps
 * {@code lastTicketSent} so fulfilled tickets aren't later flagged as never-sent).
 */
@Service
@AllArgsConstructor
public class FulfillmentService {

    private final TicketRepository ticketRepository;
    private final TicketDeliveryService ticketDeliveryService;

    @Transactional
    public void fulfill(Order order) {
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
            ticketDeliveryService.send(ticketRepository.save(ticket));
        }
    }
}
