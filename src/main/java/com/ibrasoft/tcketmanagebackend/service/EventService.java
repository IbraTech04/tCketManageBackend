package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

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
}
