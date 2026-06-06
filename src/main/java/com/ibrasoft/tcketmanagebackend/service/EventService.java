package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public Event getEventById(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
    }

    /**
     * Creates a new event with a single default zone and returns the created event.
     */
    public Event createEvent(String name, LocalDateTime date, String location, String description) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name(name)
                .time(date)
                .location(location)
                .description(description)
                .zones(new ArrayList<>())
                .build();

        Zone defaultZone = Zone.defaultZone();
        defaultZone.setEvent(event);
        event.getZones().add(defaultZone);

        return eventRepository.save(event);
    }

    public Event updateEvent(UUID id, UpdateEventRequest request) {
        Event existing = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        existing.setName(request.getName());
        existing.setTime(request.getTime());
        existing.setLocation(request.getLocation());
        existing.setDescription(request.getDescription());

        return eventRepository.save(existing);
    }

    public boolean deleteEvent(UUID id) {
        if (eventRepository.existsById(id)) {
            eventRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Adds a new zone to the specified event and returns the persisted zone. The event is reloaded
     * within this transaction so the change is tracked and flushed.
     */
    public Zone addZoneToEvent(UUID eventId, String zoneName) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        Zone newZone = Zone.builder()
                .id(UUID.randomUUID())
                .name(zoneName)
                .description(zoneName)
                .event(event)
                .build();

        event.getZones().add(newZone);
        eventRepository.save(event);
        return newZone;
    }

    @Transactional(readOnly = true)
    public List<Zone> getZonesByEvent(Event event) {
        return event.getZones();
    }

    @Transactional(readOnly = true)
    public Page<Event> getAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable);
    }
}
