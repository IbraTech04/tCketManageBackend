package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.service.EmailService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a paid order into issued tickets — one {@code ACTIVE} {@link Ticket} per {@link OrderItem}
 * seat — then hands them to {@link EmailService} for delivery.
 */
@Service
@AllArgsConstructor
public class FulfillmentService {

    private final TicketRepository ticketRepository;
    private final EmailService emailService;

    @Transactional
    public void fulfill(Order order) {
        List<Ticket> tickets = new ArrayList<>();
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
            tickets.add(ticketRepository.save(ticket));
        }
        emailService.sendTickets(order, tickets);
    }
}
