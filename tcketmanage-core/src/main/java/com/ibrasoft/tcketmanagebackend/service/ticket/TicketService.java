package com.ibrasoft.tcketmanagebackend.service.ticket;

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
import com.ibrasoft.tcketmanagebackend.utils.Patch;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-side lifecycle of individual tickets: comp issuance, holder edits, type changes, deletion.
 */
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
     * import flows). The ticket type must belong to the given event, and the seat is reserved
     * against that type's capacity exactly as an imported or purchased seat would be.
     *
     * @throws com.ibrasoft.tcketmanagebackend.exception.ConflictException if the type is sold out
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

        // TODO: Decide if admins can forcibly generate tickets even if an event is at capacity.
        inventoryService.reserve(ticketTypeId, 1);

        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .event(event)
                .ticketType(ticketType)
                .status(TicketStatus.ACTIVE)
                .build();
        return ticketRepository.save(ticket);
    }

    /**
     * Applies a <em>partial</em> update to a ticket: every field of the request is optional and a
     * {@code null} leaves the stored value untouched.
     *
     * @throws com.ibrasoft.tcketmanagebackend.exception.ConflictException if a requested type
     *         change targets a sold-out type (nothing is committed; the old seat stays held)
     */
    public Ticket updateTicket(UUID id, UpdateTicketRequest request) {
        Ticket existing = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        Patch.apply(request.getFirstName(), existing::setFirstName);
        Patch.apply(request.getLastName(), existing::setLastName);
        Patch.apply(request.getEmail(), existing::setEmail);

        applyTicketTypeChange(existing, request.getTicketTypeId());

        return ticketRepository.save(existing);
    }

    public boolean deleteTicket(UUID id) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id).orElse(null);
        if (ticket == null) {
            return false;
        }
        TicketType heldType = ticket.getTicketType();
        if (holdsSeat(ticket) && heldType != null && heldType.getId() != null) {
            inventoryService.release(heldType.getId(), 1);
        }
        ticketRepository.delete(ticket);
        return true;
    }

    /**
     * Repoints a ticket at a different ticket type, moving its seat with it. A {@code null} request
     * value or a request naming the type the ticket already has is a no-op.
     *
     * <p>SECURITY: the target type must belong to the ticket's own event. {@code createTicket}
     * has always checked this, but {@code updateTicket} did not, so {@code PUT /tickets/{id}} could
     * rebind a ticket to a <em>different</em> event's VIP type and inherit that type's zone
     * entitlements — a scanner-level privilege escalation obtained with an edit endpoint. The
     * event itself is deliberately not settable: re-homing a ticket across events is a
     * cancel-and-reissue, not a field edit.
     */
    private void applyTicketTypeChange(Ticket ticket, UUID requestedTypeId) {
        if (requestedTypeId == null) {
            return;
        }
        UUID current = ticket.getTicketType().getId();
        if (requestedTypeId.equals(current)) {
            return;
        }

        TicketType target = ticketTypeRepository.findById(requestedTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));

        UUID eventId = ticket.getEvent().getId();
        if (!target.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Ticket type does not belong to the specified event");
        }

        transferSeat(ticket, current, requestedTypeId);
        ticket.setTicketType(target);
    }

    /**
     * Moves this ticket's single seat from one ticket type to another: release the old, reserve the
     * new.
     */
    private void transferSeat(Ticket ticket, UUID fromTypeId, UUID toTypeId) {
        if (!holdsSeat(ticket)) {
            return;
        }
        if (fromTypeId.compareTo(toTypeId) < 0) {
            inventoryService.release(fromTypeId, 1);
            inventoryService.reserve(toTypeId, 1);
        } else {
            inventoryService.reserve(toTypeId, 1);
            inventoryService.release(fromTypeId, 1);
        }
    }

    /**
     * Whether this ticket is still live and therefore occupies a seat; the precondition for
     * releasing or transferring capacity on its behalf.
     */
    private boolean holdsSeat(Ticket ticket) {
        return ticket.getStatus() != TicketStatus.CANCELLED;
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
        switch (ticket.getStatus()) {
            case REVOKED -> { return ticket; }
            case CANCELLED -> throw new ConflictException(
                    "Cannot revoke ticket " + id + ": it is " + ticket.getStatus() + " (already voided by a refund)");
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
        if (ticket.getStatus() == TicketStatus.ACTIVE) {
            return ticket;
        }
        reserveSeatIfApplicable(ticket); // throws ConflictException if sold out
        ticket.setStatus(TicketStatus.ACTIVE);
        return ticketRepository.save(ticket);
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
                .timestamp(Instant.now())
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
