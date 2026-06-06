package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;

import java.util.List;

/**
 * Delivers issued tickets to the buyer/attendees. Kept behind an interface so a real SMTP +
 * signed-QR implementation can be swapped in per deployment without touching fulfillment.
 */
public interface EmailService {

    /**
     * Sends the tickets materialized for a paid order.
     */
    void sendTickets(Order order, List<Ticket> tickets);
}
