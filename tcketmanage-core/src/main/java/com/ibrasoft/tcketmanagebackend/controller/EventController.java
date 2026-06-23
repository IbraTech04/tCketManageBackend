package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.AddZoneRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateFullEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ImportConfig;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobAccepted;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.FullEventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ImportResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketTypeResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ZoneResponse;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.service.EventService;
import com.ibrasoft.tcketmanagebackend.service.ImportService;
import com.ibrasoft.tcketmanagebackend.service.TicketDeliveryService;
import com.ibrasoft.tcketmanagebackend.service.TicketService;
import com.ibrasoft.tcketmanagebackend.service.TicketTypeService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/tcket/events")
@AllArgsConstructor
public class EventController {
    private final EventService eventService;
    private final TicketService ticketService;
    private final TicketTypeService ticketTypeService;
    private final ImportService importService;
    private final TicketDeliveryService ticketDeliveryService;

    @GetMapping
    public Page<EventResponse> getAllEvents(Pageable pageable) {
        return eventService.getAllEvents(pageable).map(EventResponse::from);
    }

    @GetMapping("/{id}")
    public EventResponse getEventById(@PathVariable UUID id) {
        return EventResponse.from(eventService.getEventById(id));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event created = eventService.createEvent(
            request.getName(), request.getTime(), request.getLocation(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(created));
    }

    /**
     * Atomically creates an entire event — metadata, zones, and ticket types with their per-zone
     * entitlements — from a single wizard payload. Either the whole graph is created or nothing is.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/full")
    public ResponseEntity<FullEventResponse> createFullEvent(@Valid @RequestBody CreateFullEventRequest request) {
        EventService.EventCreationResult result = eventService.createFullEvent(request);
        FullEventResponse body = FullEventResponse.from(result.event(), result.ticketTypes());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PutMapping("/{id}")
    public EventResponse updateEvent(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return EventResponse.from(eventService.updateEvent(id, request));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
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

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
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

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(
            @PathVariable UUID id, @Valid @RequestBody CreateTicketTypeRequest request) {
        TicketTypeResponse created = TicketTypeResponse.from(ticketTypeService.createTicketType(id, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // --- Attendee roster + CSV import ---

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @GetMapping("/{id}/tickets")
    public Page<TicketResponse> getTicketsByEvent(@PathVariable UUID id, Pageable pageable) {
        return ticketService.getTicketsByEvent(id, pageable).map(TicketResponse::from);
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping(value = "/{id}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importAttendees(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") @Valid ImportConfig config) {
        ImportResult result = importService.importAttendees(id, file, config);
        HttpStatus status = result.getErrors().isEmpty() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }

    // --- Bulk ticket delivery ---

    /**
     * Re-emails every ticket for the event (typically after a CSV import, where tickets are created
     * without sending). Destructive in volume — the frontend gates this behind explicit confirmation.
     * Returns {@code 202 Accepted} immediately; live per-ticket progress and the final sent/failed
     * counts are streamed over STOMP at {@code /topic/email-jobs/{jobId}}.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/tickets/resend")
    public ResponseEntity<EmailJobAccepted> resendAllTickets(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(ticketDeliveryService.resendAll(id));
    }

    /**
     * Emails only the event's tickets that have never been successfully sent
     * ({@code lastTicketSent == null}) — a safe "fill the gaps" complement to a full resend. Async:
     * see {@link #resendAllTickets} for how progress is reported.
     */
    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PostMapping("/{id}/tickets/send-missing")
    public ResponseEntity<EmailJobAccepted> sendMissingTickets(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(ticketDeliveryService.sendMissing(id));
    }
}
