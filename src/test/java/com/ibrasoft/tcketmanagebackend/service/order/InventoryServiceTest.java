package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private TicketType type(Integer capacity, int reserved) {
        return TicketType.builder()
                .id(UUID.randomUUID())
                .name("GA")
                .capacity(capacity)
                .reservedCount(reserved)
                .build();
    }

    @Test
    void reserve_withinCapacity_increments() {
        TicketType t = type(10, 3);
        when(ticketTypeRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));

        inventoryService.reserve(t.getId(), 2);

        assertEquals(5, t.getReservedCount());
        verify(ticketTypeRepository).save(t);
    }

    @Test
    void reserve_beyondCapacity_throwsConflict() {
        TicketType t = type(5, 4);
        when(ticketTypeRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));

        assertThrows(ConflictException.class, () -> inventoryService.reserve(t.getId(), 2));
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void reserve_nullCapacity_isUnlimited() {
        TicketType t = type(null, 1000);
        when(ticketTypeRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));

        inventoryService.reserve(t.getId(), 500);

        assertEquals(1500, t.getReservedCount());
    }

    @Test
    void release_decrementsAndClampsAtZero() {
        TicketType t = type(10, 1);
        when(ticketTypeRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));

        inventoryService.release(t.getId(), 3);

        assertEquals(0, t.getReservedCount());
    }
}
