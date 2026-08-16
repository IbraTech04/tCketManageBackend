package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketResponseTest {

    private Ticket.TicketBuilder baseTicket() {
        return Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane").lastName("Doe").email("jane@example.com");
    }

    @Test
    void from_carriesHolderRef() {
        Ticket ticket = baseTicket().holderRef("lensbridge:user:7").build();

        assertEquals("lensbridge:user:7", TicketResponse.from(ticket).getHolderRef());
    }

    @Test
    void from_guestTicket_holderRefNull() {
        assertNull(TicketResponse.from(baseTicket().build()).getHolderRef());
    }
}
