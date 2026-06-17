package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reserves and releases ticket-type capacity. Every mutation is an <em>atomic conditional
 * UPDATE</em> ({@link TicketTypeRepository#reserveSeats} and friends): the capacity check and the
 * increment happen inside one SQL statement, so concurrent orders cannot oversell the last seats.
 *
 * <p>Deliberately NOT implemented as "lock the row, check in Java, save": a pessimistic-lock query
 * that resolves to an entity already in the persistence context (e.g. a {@code TicketType} loaded
 * eagerly through an order's items) acquires the DB lock but returns the stale managed instance,
 * silently turning "check under lock" into "check a pre-lock value". The atomic statements are
 * immune to that, and also shrink the row-lock window to a single statement.
 *
 * <p>Multi-row operations apply their updates in ticket-type-UUID order so concurrent transactions
 * acquire row locks in a consistent global order and cannot deadlock.
 */
@Service
@AllArgsConstructor
@Slf4j
public class InventoryService {

    private final TicketTypeRepository ticketTypeRepository;

    /**
     * Reserves {@code quantity} seats for the ticket type, or throws {@link ConflictException} if
     * insufficient capacity remains. A {@code null} capacity means unlimited.
     */
    @Transactional
    public void reserve(UUID ticketTypeId, int quantity) {
        if (ticketTypeRepository.reserveSeats(ticketTypeId, quantity) == 1) {
            return;
        }
        // The conditional update matched no row: either the type doesn't exist or it's sold out.
        // This re-read is only for the error message - the capacity decision was already made
        // atomically above, so staleness here is harmless.
        TicketType type = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found: " + ticketTypeId));
        int reserved = type.getReservedCount() == null ? 0 : type.getReservedCount();
        int available = type.getCapacity() == null ? quantity : Math.max(0, type.getCapacity() - reserved);
        throw new ConflictException(String.format(
                "Not enough capacity for ticket type '%s': requested %d, available %d",
                type.getName(), quantity, available));
    }

    /**
     * Attempts to reserve several quantities at once, all-or-nothing, returning {@code false} (rather
     * than throwing) if any ticket type lacks capacity. Used by the late-payment reconciliation path,
     * where the caller must stay in control of its own transaction: a thrown {@link ConflictException}
     * would mark the surrounding confirm transaction rollback-only and undo the status change the
     * caller still needs to commit. For the same reason, a partial reservation is undone with explicit
     * compensating decrements (the rows are already locked by this transaction) instead of a rollback.
     *
     * @param quantitiesByTicketType seats wanted per ticket type id
     * @return {@code true} if every quantity was reserved; {@code false} if none were (capacity short
     *         or a ticket type no longer exists)
     */
    @Transactional
    public boolean tryReserveAll(Map<UUID, Integer> quantitiesByTicketType) {
        // Sort by UUID to guarantee a consistent lock acquisition order across concurrent
        // transactions and prevent deadlocks when callers supply keys in different orders.
        Map<UUID, Integer> sorted = new TreeMap<>(quantitiesByTicketType);
        List<Map.Entry<UUID, Integer>> reservedSoFar = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            if (ticketTypeRepository.reserveSeats(entry.getKey(), entry.getValue()) == 1) {
                reservedSoFar.add(entry);
                continue;
            }
            for (Map.Entry<UUID, Integer> done : reservedSoFar) {
                ticketTypeRepository.releaseSeats(done.getKey(), done.getValue());
            }
            return false;
        }
        return true;
    }

    /**
     * Releases {@code quantity} previously-reserved seats. A release that would drive the count
     * negative indicates an accounting bug (e.g. a double release); it is logged loudly and clamped
     * at zero atomically.
     */
    @Transactional
    public void release(UUID ticketTypeId, int quantity) {
        if (ticketTypeRepository.releaseSeats(ticketTypeId, quantity) == 1) {
            return;
        }
        if (!ticketTypeRepository.existsById(ticketTypeId)) {
            throw new ResourceNotFoundException("TicketType not found: " + ticketTypeId);
        }
        log.warn("Releasing {} seats for ticket type {} exceeds its reserved count — "
                + "possible double release; clamping at zero", quantity, ticketTypeId);
        ticketTypeRepository.releaseSeatsClamped(ticketTypeId, quantity);
    }

    /**
     * Releases seats for several ticket types, in UUID order (see class doc on deadlock avoidance).
     */
    @Transactional
    public void releaseAll(Map<UUID, Integer> quantitiesByTicketType) {
        new TreeMap<>(quantitiesByTicketType).forEach(this::release);
    }

    /** Groups an order's items into the per-ticket-type seat counts the bulk operations consume. */
    public static Map<UUID, Integer> seatsByTicketType(Collection<OrderItem> items) {
        return items.stream().collect(Collectors.groupingBy(
                item -> item.getTicketType().getId(),
                Collectors.summingInt(item -> 1)));
    }
}
