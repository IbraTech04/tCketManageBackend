package com.ibrasoft.tcketmanagebackend.service.ticket;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
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
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final EntityManager entityManager;

    public TicketType createTicketType(UUID eventId, CreateTicketTypeRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        validateWindow(request.getSalesStartAt(), request.getSalesEndAt());

        TicketType ticketType = TicketType.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .capacity(request.getCapacity())
                .salesStartAt(request.getSalesStartAt())
                .salesEndAt(request.getSalesEndAt())
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
        TicketType existing = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found with id: " + id));
        validateWindow(request.getSalesStartAt(), request.getSalesEndAt());

        if (request.isCapacityPresent()) {
            validateCapacityReduction(existing, request.getCapacity());
        }

        existing.setName(request.getName());
        existing.setPrice(request.getPrice());
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        if (request.isCapacityPresent()) {
            existing.setCapacity(request.getCapacity());
        }
        existing.setSalesStartAt(request.getSalesStartAt());
        existing.setSalesEndAt(request.getSalesEndAt());

        // Replace the entitlement set with the requested one (orphanRemoval clears the old rows).
        // Flush the deletes before adding new rows: Hibernate's flush order is inserts-then-deletes,
        // so re-entitling a zone the ticket type already had would otherwise INSERT a duplicate
        // (ticket_type_id, zone_id) row before the old one is deleted, violating the unique constraint.
        existing.getEntitlements().clear();
        // hacky OSIV fix until we remove that requirement
        entityManager.flush();
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
     * Validates that a sales window is coherent: when both bounds are supplied, the start must fall
     * strictly before the end. A {@code null} bound is always valid (open on that side).
     */
    private void validateWindow(Instant salesStartAt, Instant salesEndAt) {
        if (salesStartAt != null && salesEndAt != null && !salesStartAt.isBefore(salesEndAt)) {
            throw new IllegalArgumentException(
                    "salesStartAt (" + salesStartAt + ") must be before salesEndAt (" + salesEndAt + ")");
        }
    }

    /**
     * Rejects an edit that would leave a ticket type holding more seats than it is allowed to sell.
     * @throws ConflictException if {@code requested} is a lower cap than the seats already held
     */
    private void validateCapacityReduction(TicketType existing, Integer requested) {
        // null means unlimited: raising the cap to "no cap" can never break the invariant.
        if (requested == null) {
            return;
        }
        // The column is NOT NULL, so this is only ever null for an instance that was never
        // persisted; treat that as zero seats held rather than throwing an NPE.
        int reserved = existing.getReservedCount() == null ? 0 : existing.getReservedCount();
        if (requested < reserved) {
            throw new ConflictException("Cannot reduce capacity of ticket type " + existing.getId()
                    + " to " + requested + ": " + reserved
                    + " seat(s) are already reserved or sold. Cancel or refund the outstanding"
                    + " orders first, or set the capacity to at least " + reserved + ".");
        }
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
