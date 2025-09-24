package com.ibrasoft.tcketmanagebackend.model.event;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private Event testEvent;
    private TicketType generalTicketType;
    private TicketType vipTicketType;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Tech Conference 2025")
                .time(LocalDateTime.of(2025, 9, 15, 9, 0))
                .location("Convention Center")
                .description("Annual technology conference")
                .build();

        generalTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General Admission")
                .zonePermissions(3L) // Zones 0,1
                .build();

        vipTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                .zonePermissions(15L) // Zones 0,1,2,3
                .build();
    }

    @Test
    void testEventCreation() {
        // Then
        assertNotNull(testEvent);
        assertEquals("Tech Conference 2025", testEvent.getName());
        assertEquals("Convention Center", testEvent.getLocation());
        assertEquals("Annual technology conference", testEvent.getDescription());
        assertEquals(LocalDateTime.of(2025, 9, 15, 9, 0), testEvent.getTime());
    }

    @Test
    void testEventWithTickets() {
        // Given
        List<Ticket> tickets = new ArrayList<>();

        Ticket generalTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .event(testEvent)
                .ticketType(generalTicketType)
                .build();

        Ticket vipTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .event(testEvent)
                .ticketType(vipTicketType)
                .build();

        tickets.add(generalTicket);
        tickets.add(vipTicket);

        // When
        Event eventWithTickets = Event.builder()
                .id(testEvent.getId())
                .name(testEvent.getName())
                .time(testEvent.getTime())
                .location(testEvent.getLocation())
                .description(testEvent.getDescription())
                .tickets(tickets)
                .build();

        // Then
        assertNotNull(eventWithTickets.getTickets());
        assertEquals(2, eventWithTickets.getTickets().size());
        assertTrue(eventWithTickets.getTickets().stream()
                .anyMatch(t -> t.getTicketType().getName().equals("General Admission")));
        assertTrue(eventWithTickets.getTickets().stream()
                .anyMatch(t -> t.getTicketType().getName().equals("VIP")));
    }

    @Test
    void testEventWithZones() {
        // Given
        List<Zone> zones = new ArrayList<>();

        Zone mainFloor = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Floor")
                .bitPosition(0)
                .event(testEvent)
                .build();

        Zone vipSection = Zone.builder()
                .id(UUID.randomUUID())
                .name("VIP Section")
                .bitPosition(1)
                .event(testEvent)
                .build();

        Zone backstage = Zone.builder()
                .id(UUID.randomUUID())
                .name("Backstage")
                .bitPosition(2)
                .event(testEvent)
                .build();

        zones.add(mainFloor);
        zones.add(vipSection);
        zones.add(backstage);

        // When
        Event eventWithZones = Event.builder()
                .id(testEvent.getId())
                .name(testEvent.getName())
                .time(testEvent.getTime())
                .location(testEvent.getLocation())
                .description(testEvent.getDescription())
                .zones(zones)
                .build();

        // Then
        assertNotNull(eventWithZones.getZones());
        assertEquals(3, eventWithZones.getZones().size());
        assertTrue(eventWithZones.getZones().stream()
                .anyMatch(z -> z.getName().equals("Main Floor") && z.getBitPosition() == 0));
        assertTrue(eventWithZones.getZones().stream()
                .anyMatch(z -> z.getName().equals("VIP Section") && z.getBitPosition() == 1));
        assertTrue(eventWithZones.getZones().stream()
                .anyMatch(z -> z.getName().equals("Backstage") && z.getBitPosition() == 2));
    }

    @Test
    void testEventWithTicketsAndZones() {
        // Given
        List<Zone> zones = new ArrayList<>();
        List<Ticket> tickets = new ArrayList<>();

        // Create zones
        Zone mainZone = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Area")
                .bitPosition(0)
                .event(testEvent)
                .build();
        zones.add(mainZone);

        // Create tickets with different access levels
        Ticket generalTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("General")
                .lastName("User")
                .email("general@example.com")
                .event(testEvent)
                .ticketType(generalTicketType) // Access to zones 0,1
                .build();

        Ticket vipTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("VIP")
                .lastName("User")
                .email("vip@example.com")
                .event(testEvent)
                .ticketType(vipTicketType) // Access to zones 0,1,2,3
                .build();

        tickets.add(generalTicket);
        tickets.add(vipTicket);

        // When
        Event completeEvent = Event.builder()
                .id(testEvent.getId())
                .name(testEvent.getName())
                .time(testEvent.getTime())
                .location(testEvent.getLocation())
                .description(testEvent.getDescription())
                .zones(zones)
                .tickets(tickets)
                .build();

        // Then
        assertNotNull(completeEvent.getZones());
        assertNotNull(completeEvent.getTickets());
        assertEquals(1, completeEvent.getZones().size());
        assertEquals(2, completeEvent.getTickets().size());

        // Verify zone permissions alignment
        assertEquals(3L, generalTicket.getTicketType().getZonePermissions());
        assertEquals(15L, vipTicket.getTicketType().getZonePermissions());
    }

    @Test
    void testEventEquality() {
        // Given
        UUID eventId = UUID.randomUUID();

        Event event1 = Event.builder()
                .id(eventId)
                .name("Test Event")
                .time(LocalDateTime.of(2025, 1, 1, 12, 0))
                .location("Test Location")
                .description("Test Description")
                .build();

        Event event2 = Event.builder()
                .id(eventId)
                .name("Test Event")
                .time(LocalDateTime.of(2025, 1, 1, 12, 0))
                .location("Test Location")
                .description("Test Description")
                .build();

        // When & Then
        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void testEventValidation() {
        // Test required fields
        assertNotNull(testEvent.getId());
        assertNotNull(testEvent.getName());
        assertNotNull(testEvent.getTime());
        assertNotNull(testEvent.getLocation());
        assertNotNull(testEvent.getDescription());

        assertFalse(testEvent.getName().isEmpty());
        assertFalse(testEvent.getLocation().isEmpty());
        assertFalse(testEvent.getDescription().isEmpty());

        // Test that event time is in the future (for this test)
        assertTrue(testEvent.getTime().isAfter(LocalDateTime.now().minusDays(1)));
    }

    @Test
    void testEventBuilder() {
        // Given
        UUID eventId = UUID.randomUUID();
        String eventName = "Builder Test Event";
        LocalDateTime eventTime = LocalDateTime.of(2025, 12, 31, 23, 59);
        String location = "Test Venue";
        String description = "Event created using builder pattern";

        // When
        Event builtEvent = Event.builder()
                .id(eventId)
                .name(eventName)
                .time(eventTime)
                .location(location)
                .description(description)
                .build();

        // Then
        assertEquals(eventId, builtEvent.getId());
        assertEquals(eventName, builtEvent.getName());
        assertEquals(eventTime, builtEvent.getTime());
        assertEquals(location, builtEvent.getLocation());
        assertEquals(description, builtEvent.getDescription());
    }
}
