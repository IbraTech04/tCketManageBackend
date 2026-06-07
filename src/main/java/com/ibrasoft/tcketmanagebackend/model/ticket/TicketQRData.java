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

    /**
     * For future proofing - allows for changes in the QR data structure while maintaining backward compatibility.
     */
    @Builder.Default
    private int version = 1;

    private UUID ticketID;
    private UUID eventID;
    private String signature;

    public static TicketQRData fromTicket(Ticket ticket) {
        return TicketQRData.builder()
                .ticketID(ticket.getID())
                .eventID(ticket.getEvent().getId())
                .build();
    }
}
