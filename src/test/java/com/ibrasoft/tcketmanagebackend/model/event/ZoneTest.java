package com.ibrasoft.tcketmanagebackend.model.event;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneTest {

    private Event testEvent;
    private Zone testZone;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .time(LocalDateTime.of(2025, 6, 15, 19, 0))
                .location("Test Venue")
                .description("Test event for zone testing")
                .build();

        testZone = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Entrance")
                .bitPosition(0)
                .event(testEvent)
                .build();
    }

    @Test
    void testZoneCreation() {
        // Then
        assertNotNull(testZone);
        assertEquals("Main Entrance", testZone.getName());
        assertEquals(0, testZone.getBitPosition());
        assertEquals(testEvent, testZone.getEvent());
        assertNotNull(testZone.getId());
    }

    @Test
    void testZoneBitPositions() {
        // Given
        Zone zone0 = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Floor")
                .bitPosition(0)
                .event(testEvent)
                .build();

        Zone zone1 = Zone.builder()
                .id(UUID.randomUUID())
                .name("VIP Area")
                .bitPosition(1)
                .event(testEvent)
                .build();

        Zone zone2 = Zone.builder()
                .id(UUID.randomUUID())
                .name("Backstage")
                .bitPosition(2)
                .event(testEvent)
                .build();

        // Then
        assertEquals(0, zone0.getBitPosition());
        assertEquals(1, zone1.getBitPosition());
        assertEquals(2, zone2.getBitPosition());

        // Verify bit positions correspond to powers of 2
        assertEquals(1L, 1L << zone0.getBitPosition()); // 2^0 = 1
        assertEquals(2L, 1L << zone1.getBitPosition()); // 2^1 = 2
        assertEquals(4L, 1L << zone2.getBitPosition()); // 2^2 = 4
    }

    @Test
    void testZoneWithTicketTypePermissions() {
        // Given - Create zones
        Zone mainFloor = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Floor")
                .bitPosition(0)
                .event(testEvent)
                .build();

        Zone vipArea = Zone.builder()
                .id(UUID.randomUUID())
                .name("VIP Area")
                .bitPosition(1)
                .event(testEvent)
                .build();

        Zone backstage = Zone.builder()
                .id(UUID.randomUUID())
                .name("Backstage")
                .bitPosition(2)
                .event(testEvent)
                .build();

        // Given - Create ticket types with different permissions
        TicketType generalType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General")
                .zonePermissions(1L) // Only Main Floor (bit 0)
                .build();

        TicketType vipType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                .zonePermissions(3L) // Main Floor + VIP Area (bits 0,1)
                .build();

        TicketType staffType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Staff")
                .zonePermissions(7L) // All areas (bits 0,1,2)
                .build();

        // When & Then - Test zone access permissions
        // General ticket should have access to Main Floor only
        assertTrue(hasZoneAccess(generalType, mainFloor));
        assertFalse(hasZoneAccess(generalType, vipArea));
        assertFalse(hasZoneAccess(generalType, backstage));

        // VIP ticket should have access to Main Floor and VIP Area
        assertTrue(hasZoneAccess(vipType, mainFloor));
        assertTrue(hasZoneAccess(vipType, vipArea));
        assertFalse(hasZoneAccess(vipType, backstage));

        // Staff ticket should have access to all areas
        assertTrue(hasZoneAccess(staffType, mainFloor));
        assertTrue(hasZoneAccess(staffType, vipArea));
        assertTrue(hasZoneAccess(staffType, backstage));
    }

    @Test
    void testDefaultZone() {
        // When
        Zone defaultZone = Zone.defaultZone();

        // Then
        assertNotNull(defaultZone);
        assertEquals("Default", defaultZone.getName());
        assertEquals(0, defaultZone.getBitPosition());
        assertNotNull(defaultZone.getId());
    }

    @Test
    void testZoneEquality() {
        // Given
        UUID zoneId = UUID.randomUUID();

        Zone zone1 = Zone.builder()
                .id(zoneId)
                .name("Test Zone")
                .bitPosition(1)
                .event(testEvent)
                .build();

        Zone zone2 = Zone.builder()
                .id(zoneId)
                .name("Test Zone")
                .bitPosition(1)
                .event(testEvent)
                .build();

        // When & Then
        assertEquals(zone1, zone2);
        assertEquals(zone1.hashCode(), zone2.hashCode());
    }

    @Test
    void testZoneValidation() {
        // Test required fields
        assertNotNull(testZone.getId());
        assertNotNull(testZone.getName());
        assertNotNull(testZone.getBitPosition());
        assertNotNull(testZone.getEvent());

        assertFalse(testZone.getName().isEmpty());
        assertTrue(testZone.getBitPosition() >= 0);
    }

    @Test
    void testZoneWithTickets() {
        // Given - Create ticket types and tickets
        TicketType generalType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General")
                .zonePermissions(1L) // Access to zone 0
                .build();

        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .event(testEvent)
                .ticketType(generalType)
                .build();

        // When & Then - Verify ticket can access the zone
        assertTrue(hasZoneAccess(ticket.getTicketType(), testZone));
    }

    @Test
    void testMultipleZonesForEvent() {
        // Given - Create multiple zones for the same event
        Zone entrance = Zone.builder()
                .id(UUID.randomUUID())
                .name("Entrance")
                .bitPosition(0)
                .event(testEvent)
                .build();

        Zone mainHall = Zone.builder()
                .id(UUID.randomUUID())
                .name("Main Hall")
                .bitPosition(1)
                .event(testEvent)
                .build();

        Zone exhibitArea = Zone.builder()
                .id(UUID.randomUUID())
                .name("Exhibit Area")
                .bitPosition(2)
                .event(testEvent)
                .build();

        // Then - Verify all zones belong to the same event
        assertEquals(testEvent, entrance.getEvent());
        assertEquals(testEvent, mainHall.getEvent());
        assertEquals(testEvent, exhibitArea.getEvent());

        // Verify unique bit positions
        assertEquals(0, entrance.getBitPosition());
        assertEquals(1, mainHall.getBitPosition());
        assertEquals(2, exhibitArea.getBitPosition());
    }

    /**
     * Helper method to check if a ticket type has access to a specific zone
     */
    private boolean hasZoneAccess(TicketType ticketType, Zone zone) {
        long zoneBit = 1L << zone.getBitPosition();
        return (ticketType.getZonePermissions() & zoneBit) > 0;
    }
}
