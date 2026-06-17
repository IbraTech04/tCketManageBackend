package com.ibrasoft.tcketmanagebackend.model.event;

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
                .description("Front door")
                .event(testEvent)
                .build();
    }

    @Test
    void testZoneCreation() {
        assertNotNull(testZone);
        assertEquals("Main Entrance", testZone.getName());
        assertEquals(testEvent, testZone.getEvent());
        assertNotNull(testZone.getId());
    }

    @Test
    void testDefaultZone() {
        Zone defaultZone = Zone.defaultZone();

        assertNotNull(defaultZone);
        assertEquals("Default", defaultZone.getName());
        assertNotNull(defaultZone.getId());
        assertNotNull(defaultZone.getDescription());
    }

    @Test
    void testZoneEquality() {
        UUID zoneId = UUID.randomUUID();

        Zone zone1 = Zone.builder().id(zoneId).name("Test Zone").description("d").event(testEvent).build();
        Zone zone2 = Zone.builder().id(zoneId).name("Test Zone").description("d").event(testEvent).build();

        assertEquals(zone1, zone2);
        assertEquals(zone1.hashCode(), zone2.hashCode());
    }

    @Test
    void testZoneValidation() {
        assertNotNull(testZone.getId());
        assertNotNull(testZone.getName());
        assertNotNull(testZone.getEvent());
        assertFalse(testZone.getName().isEmpty());
    }

    @Test
    void testMultipleZonesForEvent() {
        Zone entrance = Zone.builder().id(UUID.randomUUID()).name("Entrance").description("d").event(testEvent).build();
        Zone mainHall = Zone.builder().id(UUID.randomUUID()).name("Main Hall").description("d").event(testEvent).build();
        Zone exhibitArea = Zone.builder().id(UUID.randomUUID()).name("Exhibit Area").description("d").event(testEvent).build();

        assertEquals(testEvent, entrance.getEvent());
        assertEquals(testEvent, mainHall.getEvent());
        assertEquals(testEvent, exhibitArea.getEvent());
    }
}
