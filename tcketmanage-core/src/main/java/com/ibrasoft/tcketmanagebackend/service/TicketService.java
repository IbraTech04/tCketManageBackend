package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Transactional
public class TicketService {

    private TicketRepository ticketRepository;
    private ScanEventRepository scanEventRepository;
    private TicketTypeRepository ticketTypeRepository;
    private EventRepository eventRepository;
    private InventoryService inventoryService;

    /**
     * Issues a single comp/admin ticket bound to an event and ticket type (outside the order and
     * import flows). The ticket type must belong to the given event.
     */
    public Ticket createTicket(String firstName, String lastName, String email,
                               UUID eventId, UUID ticketTypeId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));

        if (ticketType.getEvent() == null || !ticketType.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Ticket type does not belong to the specified event");
        }

        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .event(event)
                .ticketType(ticketType)
                .build();
        return ticketRepository.save(ticket);
    }

    public Ticket updateTicket(UUID id, UpdateTicketRequest request) {
        Ticket existing = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        existing.setFirstName(request.getFirstName());
        existing.setLastName(request.getLastName());
        existing.setEmail(request.getEmail());

        if (request.getTicketTypeId() != null) {
            TicketType ticketType = ticketTypeRepository.findById(request.getTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));
            existing.setTicketType(ticketType);
        }

        return ticketRepository.save(existing);
    }

    public boolean deleteTicket(UUID id) {
        if (ticketRepository.existsById(id)) {
            ticketRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Revokes a ticket: marks it {@code REVOKED} (kept in the DB for the audit trail, rejected at scan
     * time) and releases its seat back to inventory if it consumed one. The ticket row is locked so a
     * concurrent revoke/reactivate can't double-release. Idempotent: an already-{@code REVOKED} ticket
     * is a no-op. A {@code CANCELLED} (refunded) ticket cannot be revoked — its seat is already
     * released, and revoking would double-release.
     *
     * <p>Only order-issued tickets ({@code order != null}) hold an inventory seat; comp/import tickets
     * never incremented {@code reservedCount}, so revoking them leaves capacity untouched.
     */
    public Ticket revokeTicket(UUID id) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        TicketStatus status = currentStatus(ticket);
        switch (status) {
            case REVOKED -> { return ticket; }
            case CANCELLED -> throw new ConflictException(
                    "Cannot revoke ticket " + id + ": it is " + status + " (already voided by a refund)");
            case ACTIVE -> {
                releaseSeatIfReserved(ticket);
                ticket.setStatus(TicketStatus.REVOKED);
            }
        }
        return ticketRepository.save(ticket);
    }

    /**
     * Reactivates a previously {@code REVOKED} or {@code CANCELLED} ticket back to {@code ACTIVE},
     * re-reserving its seat if it is order-issued. Throws {@link ConflictException} if the ticket type
     * is now sold out (the reservation fails and the reactivation rolls back). Row-locked and
     * idempotent: an already-{@code ACTIVE} ticket is a no-op.
     */
    public Ticket reactivateTicket(UUID id) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (currentStatus(ticket) == TicketStatus.ACTIVE) {
            return ticket;
        }
        reserveSeatIfApplicable(ticket); // throws ConflictException if sold out
        ticket.setStatus(TicketStatus.ACTIVE);
        return ticketRepository.save(ticket);
    }

    // TODO: Consider removing this... not entirely necessary since the model should be updated instead
    /** Legacy rows may predate the status column; treat a missing status as {@code ACTIVE}. */
    private static TicketStatus currentStatus(Ticket ticket) {
        return ticket.getStatus() == null ? TicketStatus.ACTIVE : ticket.getStatus();
    }

    private void releaseSeatIfReserved(Ticket ticket) {
        if (ticket.getOrder() != null && ticket.getTicketType() != null) {
            inventoryService.release(ticket.getTicketType().getId(), 1);
        }
    }

    private void reserveSeatIfApplicable(Ticket ticket) {
        if (ticket.getOrder() != null && ticket.getTicketType() != null) {
            inventoryService.reserve(ticket.getTicketType().getId(), 1);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findTicketById(UUID id) {
        return ticketRepository.findById(id);
    }

    public void recordTicketScan(Ticket ticket, Zone zone) {
        scanEventRepository.save(ScanEvent.builder()
                .ticketId(ticket.getID())
                .zone(zone)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public int getZoneEntryCount(Ticket ticket, Zone zone) {
        return scanEventRepository.countZoneEntriesByTicketId(ticket.getID(), zone.getId());
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByEvent(UUID id, Pageable pageable) {
        return ticketRepository.findByEvent(id, pageable);
    }
}
