package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private String eventName;
    private LocalDateTime eventDate;
    private String eventDescription;
    private Event mockEvent;

    @BeforeEach
    void setUp() {
        eventName = "Test Concert";
        eventDate = LocalDateTime.of(2025, 12, 31, 20, 0);
        eventDescription = "New Year's Eve Concert";

        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .name(eventName)
                .time(eventDate)
                .description(eventDescription)
                .location("Concert Hall")
                .zones(List.of(Zone.defaultZone()))
                .build();
    }

    @Test
    void testCreateEvent_Success() {
        // Given
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Event createdEvent = eventService.createEvent(eventName, eventDate, eventDescription);

        // Then
        assertNotNull(createdEvent);
        assertEquals(eventName, createdEvent.getName());
        assertEquals(eventDate, createdEvent.getTime());
        assertEquals(eventDescription, createdEvent.getDescription());
        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    void testGetEventById_Success() {
        // Given
        UUID eventId = mockEvent.getId();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));

        // When
        Event foundEvent = eventService.getEventById(eventId);

        // Then
        assertNotNull(foundEvent);
        assertEquals(eventId, foundEvent.getId());
        assertEquals(eventName, foundEvent.getName());
        verify(eventRepository, times(1)).findById(eventId);
    }

    @Test
    void testGetEventById_NotFound() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> eventService.getEventById(id));
    }

    @Test
    void testGetAllEvents_Success() {
        // Given
        Page<Event> eventPage = new PageImpl<>(Collections.singletonList(mockEvent));
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.findAll(pageable)).thenReturn(eventPage);

        // When
        Page<Event> result = eventService.getAllEvents(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(mockEvent.getName(), result.getContent().get(0).getName());
        verify(eventRepository, times(1)).findAll(pageable);
    }

    @Test
    void testGetZonesByEvent_Success() {
        // Given an event with two zones
        Zone z1 = Zone.builder().id(UUID.randomUUID()).name("Main").bitPosition(0).event(mockEvent).build();
        Zone z2 = Zone.builder().id(UUID.randomUUID()).name("VIP").bitPosition(1).event(mockEvent).build();
        Event eventWithZones = Event.builder()
                .id(mockEvent.getId())
                .name(mockEvent.getName())
                .time(mockEvent.getTime())
                .description(mockEvent.getDescription())
                .location(mockEvent.getLocation())
                .zones(new ArrayList<>(List.of(z1, z2)))
                .build();

        // When
        var zones = eventService.getZonesByEvent(eventWithZones);

        // Then
        assertEquals(2, zones.size());
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals("Main")));
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals("VIP")));
    }

    @Test
    void testAddZoneToEvent_AssignsNextBitPosition() {
        // Given existing event with one zone
        List<Zone> zones = new ArrayList<>();
        zones.add(Zone.builder().id(UUID.randomUUID()).name("Entrance").bitPosition(0).build());
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name("E1")
                .time(LocalDateTime.now())
                .description("D")
                .zones(zones)
                .build();

        // When
        eventService.addZoneToEvent(event, "Hall");

        // Then
        assertEquals(2, event.getZones().size());
        Zone newZone = event.getZones().get(1);
        assertEquals("Hall", newZone.getName());
        assertEquals(1, newZone.getBitPosition());
        assertEquals(event, newZone.getEvent());
    }
}
