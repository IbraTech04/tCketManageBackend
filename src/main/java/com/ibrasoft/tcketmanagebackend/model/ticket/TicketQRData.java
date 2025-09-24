package com.ibrasoft.tcketmanagebackend.model.ticket;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TicketQRData {
    private UUID ticketID;
    private String signature;

    public static TicketQRData fromTicket(Ticket ticket) {
        return TicketQRData.builder()
                .ticketID(ticket.getID())
                .build();
    }
}
