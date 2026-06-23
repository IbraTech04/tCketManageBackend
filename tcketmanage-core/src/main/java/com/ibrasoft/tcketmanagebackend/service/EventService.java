package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateFullEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateEventRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.WizardEntitlementRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.WizardTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.WizardZoneRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;

    @Transactional(readOnly = true)
    public Event getEventById(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
    }

    /**
     * Creates a new event with a single default zone and returns the created event.
     */
    public Event createEvent(String name, OffsetDateTime date, String location, String description) {
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

    /**
     * Atomically creates an event together with its zones and ticket types (the "creation wizard"
     * payload). Zones are referenced by ticket-type entitlements via the client-supplied wizard
     * keys; this method generates the real zone UUIDs and resolves those keys. Runs in a single
     * transaction, so a bad reference (e.g. an entitlement pointing at an unknown zone key) rolls
     * back the entire graph and nothing is persisted.
     */
    public EventCreationResult createFullEvent(CreateFullEventRequest request) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .time(request.getTime())
                .location(request.getLocation())
                .description(request.getDescription())
                .zones(new ArrayList<>())
                .build();

        // Build zones, mapping each wizard key to its freshly created Zone.
        Map<String, Zone> zonesByKey = new HashMap<>();
        for (WizardZoneRequest zoneRequest : request.getZones()) {
            if (zonesByKey.containsKey(zoneRequest.getKey())) {
                throw new IllegalArgumentException("Duplicate zone key: " + zoneRequest.getKey());
            }
            String description = zoneRequest.getDescription() != null && !zoneRequest.getDescription().isBlank()
                    ? zoneRequest.getDescription()
                    : zoneRequest.getName();
            Zone zone = Zone.builder()
                    .id(UUID.randomUUID())
                    .name(zoneRequest.getName())
                    .description(description)
                    .event(event)
                    .build();
            event.getZones().add(zone);
            zonesByKey.put(zoneRequest.getKey(), zone);
        }

        // Persist the event first so its cascade saves the zones (giving them DB identity).
        Event savedEvent = eventRepository.save(event);

        // Build and persist ticket types, resolving entitlement zone keys against the saved zones.
        List<TicketType> ticketTypes = new ArrayList<>();
        for (WizardTicketTypeRequest typeRequest : request.getTicketTypes()) {
            TicketType ticketType = TicketType.builder()
                    .event(savedEvent)
                    .name(typeRequest.getName())
                    .price(typeRequest.getPrice())
                    .isActive(typeRequest.getIsActive() == null || typeRequest.getIsActive())
                    .capacity(typeRequest.getCapacity())
                    .entitlements(new ArrayList<>())
                    .build();

            for (WizardEntitlementRequest entitlementRequest : typeRequest.getEntitlements()) {
                Zone zone = zonesByKey.get(entitlementRequest.getZoneKey());
                if (zone == null) {
                    throw new IllegalArgumentException(
                            "Ticket type '" + typeRequest.getName() + "' references unknown zone key: "
                                    + entitlementRequest.getZoneKey());
                }
                ticketType.getEntitlements().add(ZoneEntitlement.builder()
                        .ticketType(ticketType)
                        .zone(zone)
                        .maxEntries(entitlementRequest.getMaxEntries())
                        .build());
            }
            ticketTypes.add(ticketTypeRepository.save(ticketType));
        }

        return new EventCreationResult(savedEvent, ticketTypes);
    }

    /** Domain result of {@link #createFullEvent}: the persisted event and its ticket types. */
    public record EventCreationResult(Event event, List<TicketType> ticketTypes) {
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
