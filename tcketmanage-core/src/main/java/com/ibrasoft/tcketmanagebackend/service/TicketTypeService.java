package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ZoneEntitlementRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;

    public TicketType createTicketType(UUID eventId, CreateTicketTypeRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        TicketType ticketType = TicketType.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .entitlements(new ArrayList<>())
                .build();

        applyEntitlements(ticketType, event.getId(), request.getEntitlements());
        return ticketTypeRepository.save(ticketType);
    }

    @Transactional(readOnly = true)
    public Page<TicketType> getAllTicketTypes(Pageable pageable) {
        return ticketTypeRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<TicketType> findById(UUID id) {
        return ticketTypeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<TicketType> getTicketTypesByEvent(UUID eventId) {
        return ticketTypeRepository.findByEvent_IdAndIsActive(eventId, true);
    }

    public TicketType updateTicketType(UUID id, UpdateTicketTypeRequest request) {
        // Locked load: the flush rewrites the row, so reading it unlocked would let a concurrent
        // seat reservation land between load and flush and be silently overwritten.
        TicketType existing = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found with id: " + id));

        existing.setName(request.getName());
        existing.setPrice(request.getPrice());
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());

        // Replace the entitlement set with the requested one (orphanRemoval clears the old rows).
        existing.getEntitlements().clear();
        applyEntitlements(existing, existing.getEvent().getId(), request.getEntitlements());

        return ticketTypeRepository.save(existing);
    }

    public boolean deleteTicketType(UUID id) {
        if (ticketTypeRepository.existsById(id)) {
            ticketTypeRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Builds {@link ZoneEntitlement} rows from the request and attaches them to the ticket type,
     * validating that every referenced zone belongs to the ticket type's event.
     */
    private void applyEntitlements(TicketType ticketType, UUID eventId,
                                   List<ZoneEntitlementRequest> requests) {
        if (requests == null) {
            return;
        }
        for (ZoneEntitlementRequest req : requests) {
            Zone zone = zoneRepository.findById(req.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + req.getZoneId()));
            if (zone.getEvent() == null || !zone.getEvent().getId().equals(eventId)) {
                throw new IllegalArgumentException(
                    "Zone " + req.getZoneId() + " does not belong to event " + eventId);
            }
            ticketType.getEntitlements().add(ZoneEntitlement.builder()
                    .ticketType(ticketType)
                    .zone(zone)
                    .maxEntries(req.getMaxEntries())
                    .build());
        }
    }
}
