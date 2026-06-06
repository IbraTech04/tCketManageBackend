package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.AddZoneRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ImportConfig;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ImportResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketTypeResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ZoneResponse;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.security.AdminGuard;
import com.ibrasoft.tcketmanagebackend.service.EventService;
import com.ibrasoft.tcketmanagebackend.service.ImportService;
import com.ibrasoft.tcketmanagebackend.service.TicketService;
import com.ibrasoft.tcketmanagebackend.service.TicketTypeService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Events and their child collections (zones, ticket types, attendee roster, CSV imports).
 * Individual zones/ticket-types/tickets are addressed at their own top-level controllers.
 */
@RestController
@RequestMapping("/api/v1/events")
@AllArgsConstructor
public class EventController {
    private final EventService eventService;
    private final TicketService ticketService;
    private final TicketTypeService ticketTypeService;
    private final ImportService importService;
    private final AdminGuard adminGuard;

    @GetMapping
    public Page<EventResponse> getAllEvents(Pageable pageable) {
        return eventService.getAllEvents(pageable).map(EventResponse::from);
    }

    @GetMapping("/{id}")
    public EventResponse getEventById(@PathVariable UUID id) {
        return EventResponse.from(eventService.getEventById(id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event created = eventService.createEvent(
            request.getName(), request.getTime(), request.getLocation(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(created));
    }

    @PutMapping("/{id}")
    public EventResponse updateEvent(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return EventResponse.from(eventService.updateEvent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        return eventService.deleteEvent(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // --- Zones (sub-resource) ---

    @GetMapping("/{id}/zones")
    public List<ZoneResponse> getZonesByEvent(@PathVariable UUID id) {
        Event event = eventService.getEventById(id);
        return eventService.getZonesByEvent(event).stream()
                .map(ZoneResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/zones")
    public ResponseEntity<ZoneResponse> addZoneToEvent(@PathVariable UUID id, @RequestBody AddZoneRequest request) {
        ZoneResponse created = ZoneResponse.from(eventService.addZoneToEvent(id, request.getZoneName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // --- Ticket types (sub-resource) ---

    @GetMapping("/{id}/ticket-types")
    public List<TicketTypeResponse> getTicketTypesByEvent(@PathVariable UUID id) {
        return ticketTypeService.getTicketTypesByEvent(id).stream()
                .map(TicketTypeResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(
            @PathVariable UUID id, @Valid @RequestBody CreateTicketTypeRequest request) {
        TicketTypeResponse created = TicketTypeResponse.from(ticketTypeService.createTicketType(id, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // --- Attendee roster + CSV import ---

    @GetMapping("/{id}/tickets")
    public Page<TicketResponse> getTicketsByEvent(@PathVariable UUID id, Pageable pageable) {
        return ticketService.getTicketsByEvent(id, pageable).map(TicketResponse::from);
    }

    @PostMapping(value = "/{id}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importAttendees(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") @Valid ImportConfig config,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        adminGuard.require(adminToken);
        ImportResult result = importService.importAttendees(id, file, config);
        HttpStatus status = result.getErrors().isEmpty() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
