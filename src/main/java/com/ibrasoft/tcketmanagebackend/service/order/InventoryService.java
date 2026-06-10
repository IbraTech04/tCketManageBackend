package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reserves and releases ticket-type capacity. All mutations take a pessimistic write lock on the
 * {@link TicketType} row so concurrent orders cannot oversell the last seats.
 */
@Service
@AllArgsConstructor
public class InventoryService {

    private final TicketTypeRepository ticketTypeRepository;

    /**
     * Reserves {@code quantity} seats for the ticket type, or throws {@link ConflictException} if
     * insufficient capacity remains. A {@code null} capacity means unlimited.
     */
    @Transactional
    public void reserve(UUID ticketTypeId, int quantity) {
        TicketType type = lock(ticketTypeId);
        Integer capacity = type.getCapacity();
        int reserved = type.getReservedCount() == null ? 0 : type.getReservedCount();
        if (capacity != null && reserved + quantity > capacity) {
            throw new ConflictException(String.format(
                "Not enough capacity for ticket type '%s': requested %d, available %d",
                type.getName(), quantity, Math.max(0, capacity - reserved)));
        }
        type.setReservedCount(reserved + quantity);
        ticketTypeRepository.save(type);
    }

    /**
     * Attempts to reserve several quantities at once, all-or-nothing, returning {@code false} (rather
     * than throwing) if any ticket type lacks capacity. Used by the late-payment reconciliation path,
     * where the caller must stay in control of its own transaction: a thrown {@link ConflictException}
     * would mark the surrounding confirm transaction rollback-only and undo the status change the
     * caller still needs to commit. All rows are locked and checked before any is incremented, so a
     * partial reservation can never be left behind.
     *
     * @param quantitiesByTicketType seats wanted per ticket type id
     * @return {@code true} if every quantity was reserved; {@code false} if none were (capacity short)
     */
    @Transactional
    public boolean tryReserveAll(Map<UUID, Integer> quantitiesByTicketType) {
        // Sort by UUID to guarantee a consistent lock acquisition order across concurrent
        // transactions and prevent deadlocks when callers supply keys in different orders.
        Map<UUID, Integer> sorted = new TreeMap<>(quantitiesByTicketType);
        Map<UUID, TicketType> locked = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            TicketType type = lock(entry.getKey());
            locked.put(entry.getKey(), type);
            Integer capacity = type.getCapacity();
            int reserved = type.getReservedCount() == null ? 0 : type.getReservedCount();
            if (capacity != null && reserved + entry.getValue() > capacity) {
                return false; // no increments performed yet → nothing to undo
            }
        }
        for (Map.Entry<UUID, Integer> entry : sorted.entrySet()) {
            TicketType type = locked.get(entry.getKey());
            int reserved = type.getReservedCount() == null ? 0 : type.getReservedCount();
            type.setReservedCount(reserved + entry.getValue());
            ticketTypeRepository.save(type);
        }
        return true;
    }

    /**
     * Releases {@code quantity} previously-reserved seats, clamping at zero.
     */
    @Transactional
    public void release(UUID ticketTypeId, int quantity) {
        TicketType type = lock(ticketTypeId);
        int reserved = type.getReservedCount() == null ? 0 : type.getReservedCount();
        type.setReservedCount(Math.max(0, reserved - quantity));
        ticketTypeRepository.save(type);
    }

    private TicketType lock(UUID ticketTypeId) {
        return ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found: " + ticketTypeId));
    }
}
