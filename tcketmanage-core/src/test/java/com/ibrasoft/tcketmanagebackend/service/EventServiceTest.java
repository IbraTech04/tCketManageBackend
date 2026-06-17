package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
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
    private String eventLocation;
    private String eventDescription;
    private Event mockEvent;

    @BeforeEach
    void setUp() {
        eventName = "Test Concert";
        eventDate = LocalDateTime.of(2025, 12, 31, 20, 0);
        eventLocation = "Concert Hall";
        eventDescription = "New Year's Eve Concert";

        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .name(eventName)
                .time(eventDate)
                .description(eventDescription)
                .location(eventLocation)
                .zones(new ArrayList<>(List.of(Zone.defaultZone())))
                .build();
    }

    @Test
    void testCreateEvent_Success() {
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event createdEvent = eventService.createEvent(eventName, eventDate, eventLocation, eventDescription);

        assertNotNull(createdEvent);
        assertEquals(eventName, createdEvent.getName());
        assertEquals(eventDate, createdEvent.getTime());
        assertEquals(eventLocation, createdEvent.getLocation());
        assertEquals(eventDescription, createdEvent.getDescription());
        // A default zone is created and linked back to the event
        assertEquals(1, createdEvent.getZones().size());
        assertEquals(createdEvent, createdEvent.getZones().get(0).getEvent());
        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    void testGetEventById_Success() {
        UUID eventId = mockEvent.getId();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(mockEvent));

        Event foundEvent = eventService.getEventById(eventId);

        assertNotNull(foundEvent);
        assertEquals(eventId, foundEvent.getId());
        assertEquals(eventName, foundEvent.getName());
        verify(eventRepository, times(1)).findById(eventId);
    }

    @Test
    void testGetEventById_NotFound() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> eventService.getEventById(id));
    }

    @Test
    void testGetAllEvents_Success() {
        Page<Event> eventPage = new PageImpl<>(Collections.singletonList(mockEvent));
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.findAll(pageable)).thenReturn(eventPage);

        Page<Event> result = eventService.getAllEvents(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(mockEvent.getName(), result.getContent().get(0).getName());
        verify(eventRepository, times(1)).findAll(pageable);
    }

    @Test
    void testGetZonesByEvent_Success() {
        Zone z1 = Zone.builder().id(UUID.randomUUID()).name("Main").event(mockEvent).build();
        Zone z2 = Zone.builder().id(UUID.randomUUID()).name("VIP").event(mockEvent).build();
        Event eventWithZones = Event.builder()
                .id(mockEvent.getId())
                .name(mockEvent.getName())
                .time(mockEvent.getTime())
                .description(mockEvent.getDescription())
                .location(mockEvent.getLocation())
                .zones(new ArrayList<>(List.of(z1, z2)))
                .build();

        var zones = eventService.getZonesByEvent(eventWithZones);

        assertEquals(2, zones.size());
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals("Main")));
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals("VIP")));
    }

    @Test
    void testAddZoneToEvent_AddsAndReturnsZone() {
        List<Zone> zones = new ArrayList<>();
        zones.add(Zone.builder().id(UUID.randomUUID()).name("Entrance").description("d").build());
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .name("E1")
                .time(LocalDateTime.now())
                .location("L1")
                .description("D")
                .zones(zones)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Zone created = eventService.addZoneToEvent(eventId, "Hall");

        assertEquals("Hall", created.getName());
        assertEquals(event, created.getEvent());
        assertEquals(2, event.getZones().size());
    }
}
