package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

    private Event testEvent;
    private TicketType generalTicketType;
    private TicketType vipTicketType;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .time(OffsetDateTime.now())
                .location("Test Location")
                .description("Test Description")
                .build();

        generalTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General Admission")
                 // Binary: 011 (access to zones 0 and 1)
                .build();

        vipTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                 // Binary: 1111 (access to zones 0, 1, 2, 3)
                .build();
    }

    @Test
    void testTicketCreation() {
        // Given
        UUID ticketId = UUID.randomUUID();
        String firstName = "John";
        String lastName = "Doe";
        String email = "john.doe@example.com";

        // When
        Ticket ticket = Ticket.builder()
                .ID(ticketId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .event(testEvent)
                .ticketType(generalTicketType)
                .build();

        // Then
        assertNotNull(ticket);
        assertEquals(ticketId, ticket.getID());
        assertEquals(firstName, ticket.getFirstName());
        assertEquals(lastName, ticket.getLastName());
        assertEquals(email, ticket.getEmail());
        assertEquals(testEvent, ticket.getEvent());
        assertEquals(generalTicketType, ticket.getTicketType());
    }

    @Test
    void testTicketWithVIPType() {
        // Given & When
        Ticket vipTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .event(testEvent)
                .ticketType(vipTicketType)
                .build();

        // Then
        assertEquals("VIP", vipTicket.getTicketType().getName());
    }

    @Test
    void testTicketWithNullTicketType() {
        // Given & When
        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .event(testEvent)
                .ticketType(null)
                .build();

        // Then
        assertNotNull(ticket);
        assertNull(ticket.getTicketType());
    }

    @Test
    void testTicketEquality() {
        // Given
        UUID ticketId = UUID.randomUUID();

        Ticket ticket1 = Ticket.builder()
                .ID(ticketId)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .event(testEvent)
                .ticketType(generalTicketType)
                .build();

        Ticket ticket2 = Ticket.builder()
                .ID(ticketId)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .event(testEvent)
                .ticketType(generalTicketType)
                .build();

        // When & Then
        assertEquals(ticket1, ticket2);
        assertEquals(ticket1.hashCode(), ticket2.hashCode());
    }

    @Test
    void testTicketValidation() {
        // Test that ticket maintains required fields
        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Valid")
                .lastName("User")
                .email("valid@example.com")
                .event(testEvent)
                .ticketType(generalTicketType)
                .build();

        assertNotNull(ticket.getID());
        assertNotNull(ticket.getFirstName());
        assertNotNull(ticket.getLastName());
        assertNotNull(ticket.getEmail());
        assertNotNull(ticket.getEvent());
        assertNotNull(ticket.getTicketType());

        assertTrue(ticket.getEmail().contains("@"));
    }
}
