package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
