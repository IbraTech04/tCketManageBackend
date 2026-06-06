package com.ibrasoft.tcketmanagebackend.model.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQRData {
    private UUID ticketID;
    private String signature;

    public static TicketQRData fromTicket(Ticket ticket) {
        return TicketQRData.builder()
                .ticketID(ticket.getID())
                .build();
    }
}
