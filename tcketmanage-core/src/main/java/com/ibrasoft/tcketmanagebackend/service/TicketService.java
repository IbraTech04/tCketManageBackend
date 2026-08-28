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

        // CAPACITY: a comp ticket consumes a seat like any other, so it takes one through the same
        // atomic conditional UPDATE ImportService uses (ImportService:110). Reserve BEFORE the row
        // is written, so a sold-out type throws ConflictException (409) and rolls this transaction
        // back with no ticket persisted. Rejected alternative: exempting admin-issued tickets from
        // the reserve "because an operator knows what they're doing" — that is precisely how
        // reserved_count drifted below the live seat count, silently reselling seats already handed
        // out and letting the venue oversell.
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
        // Locked load: status and ticketType read below decide which seats move, so they must be
        // read under the row lock — see the class javadoc on concurrent edits double-moving a seat.
        Ticket existing = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        // Values arrive already trimmed: UpdateTicketRequest normalises on binding so its own
        // @Email / @Size constraints see the real value rather than one padded with whitespace.
        // TODO: Update this with the same Patch system as Minbar
        if (request.getFirstName() != null) {
            existing.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            existing.setLastName(request.getLastName());
        }
        if (request.getEmail() != null) {
            existing.setEmail(request.getEmail());
        }

        applyTicketTypeChange(existing, request.getTicketTypeId());

        return ticketRepository.save(existing);
    }

    public boolean deleteTicket(UUID id) {
        // Locked load, for the same reason as updateTicket: without it a concurrent type change
        // could commit between this read and the release, sending the seat back to the type the
        // ticket no longer sits on.
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
        TicketType current = ticket.getTicketType();
        UUID currentTypeId = current == null ? null : current.getId();
        if (requestedTypeId.equals(currentTypeId)) {
            return; // same type — no inventory movement, and no self-deadlock on one row
        }

        TicketType target = ticketTypeRepository.findById(requestedTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));

        UUID eventId = ticket.getEvent() == null ? null : ticket.getEvent().getId();
        if (eventId == null || target.getEvent() == null
                || !target.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Ticket type does not belong to the specified event");
        }

        transferSeat(ticket, currentTypeId, requestedTypeId);
        ticket.setTicketType(target);
    }

    /**
     * Moves this ticket's single seat from one ticket type to another: release the old, reserve the
     * new.
     *
     * <p>Failure semantics are all-or-nothing by rollback. {@link InventoryService#reserve} throws
     * {@link com.ibrasoft.tcketmanagebackend.exception.ConflictException} when its conditional
     * UPDATE matches no row, and that exception is deliberately allowed to propagate: it marks this
     * transaction rollback-only, so a sold-out target undoes the release of the old type as well and
     * the ticket keeps the seat it already had. Rejected alternative: the explicit compensating
     * decrement {@link InventoryService#tryReserveAll} uses. That exists because the late-payment
     * path still has to commit an order status change after a failed reservation; here there is
     * nothing left to commit, so a rollback is both simpler and strictly safer than hand-rolled
     * compensation.
     *
     * <p>The two atomic UPDATEs are applied in ticket-type-UUID order, per the global lock ordering
     * in {@code docs/LOCKING.MD} (the same rule {@code InventoryService.tryReserveAll} and
     * {@code releaseAll} follow with a {@code TreeMap}). Without it, two operators transferring
     * tickets in opposite directions between the same pair of types would grab each other's row
     * locks in opposite order and deadlock.
     *
     * <p>A {@code CANCELLED} ticket holds no seat, so its type change moves no inventory: there is
     * nothing to give back on the old type, and reserving on the new one would conjure a seat that
     * no live ticket occupies.
     */
    private void transferSeat(Ticket ticket, UUID fromTypeId, UUID toTypeId) {
        if (!holdsSeat(ticket)) {
            return;
        }
        if (fromTypeId == null) {
            // Reachable only for legacy/repaired rows — Ticket.ticketType is @NotNull. The ticket is
            // live but holds no seat, so this is a plain acquisition with nothing to give back.
            inventoryService.reserve(toTypeId, 1);
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
     * Whether this ticket is still live and therefore occupies a seat — the precondition for
     * releasing or transferring capacity on its behalf.
     *
     * <p>Phrased as "not {@code CANCELLED}" rather than "is {@code ACTIVE}" on purpose. The
     * {@code status} column is nullable with no default
     * ({@code V1__create_core_schema.sql}: {@code status varchar(20) CHECK (...)}), and
     * {@link Ticket}'s {@code @Builder.Default} only applies on the Lombok builder path — the
     * no-args constructor, {@code @AllArgsConstructor}, and any host-side repair SQL all bypass it.
     * A {@code NULL}-status row is live and holds a seat, so testing for {@code ACTIVE} would treat
     * it as holding none: deleting it would shrink capacity forever and a type change would move
     * the ticket without moving its seat. Between the two failure directions, only "a live ticket
     * is assumed to hold a seat" is the safe one — the mirror error oversells.
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
