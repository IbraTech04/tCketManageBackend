package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.service.EventService;
import com.ibrasoft.tcketmanagebackend.service.TicketService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController()
@RequestMapping("/api/v1/events")
@AllArgsConstructor
public class EventController {
    private final EventService eventService;
    private final TicketService ticketService;

    @GetMapping()
    public Page<Event> getAllEvents(Pageable pageable) {
        return eventService.getAllEvents(pageable);
    }

    @GetMapping("/{id}")
    public Event getEventById(@PathVariable UUID id) {
        return eventService.getEventById(id);
    }

    @GetMapping("/{id}/zones")
    public List<Zone> getZonesByEvent(@PathVariable UUID id) {
        Event event = eventService.getEventById(id);
        return eventService.getZonesByEvent(event);
    }

    @GetMapping("/{id}/attendees")
    public Page<Ticket> getAttendeesByEvent(@PathVariable UUID id, Pageable pageable) {
        return ticketService.getTicketsByEvent(id, pageable);
    }

    /**
     * TODO: Implement Ticket Types
     */
//    @GetMapping("/{id}/types")
//    public List<TicketType> getTicketTypesByEvent(@PathVariable UUID id) {
//        Event event = eventService.getEventById(id);
//        return eventService.getTicketTypesByEvent(event);
//    }

}
