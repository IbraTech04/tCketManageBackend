package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    public Event getEventById(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    /**
     * Creates a new event and returns the created event object.
     * @return
     */
    public Event createEvent(String name, LocalDateTime date, String description) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name(name)
                .time(date)
                .description(description)
                .zones(List.of(Zone.defaultZone()))
                .build();
        return eventRepository.save(event);
    }

    /**
     * Adds a new zone to the specified event. The new zone is assigned the next available bit position.
     * @param event The event to which the zone will be added.
     * @param zoneName The name of the new zone.
     */
    public void addZoneToEvent(Event event, String zoneName) {
        Zone newZone = Zone.builder()
                .id(UUID.randomUUID())
                .name(zoneName)
                .bitPosition(event.getZones().size()) // Assign next available bit position
                .event(event)
                .build();
        event.getZones().add(newZone);
    }

    public List<Zone> getZonesByEvent(Event event) {
        return event.getZones();
    }

    public Page<Event> getAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable);
    }
}
