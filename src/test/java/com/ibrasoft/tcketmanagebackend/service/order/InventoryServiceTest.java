package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
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

    @Test
    void tryReserveAll_allFit_reservesEveryTypeAndReturnsTrue() {
        TicketType a = type(10, 3);
        TicketType b = type(5, 1);
        when(ticketTypeRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(ticketTypeRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));

        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        wanted.put(a.getId(), 2);
        wanted.put(b.getId(), 1);

        assertTrue(inventoryService.tryReserveAll(wanted));
        assertEquals(5, a.getReservedCount());
        assertEquals(2, b.getReservedCount());
    }

    @Test
    void tryReserveAll_oneTypeSoldOut_returnsFalseWithoutIncrementing() {
        TicketType a = type(10, 3);
        TicketType full = type(5, 5);
        // tryReserveAll locks rows in UUID order (deadlock-safe), so which type is checked first is
        // non-deterministic with random ids — and if 'full' sorts first it short-circuits before 'a'
        // is ever locked. Stub leniently: the outcome (false, nothing reserved) holds either way.
        lenient().when(ticketTypeRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        lenient().when(ticketTypeRepository.findByIdForUpdate(full.getId())).thenReturn(Optional.of(full));

        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        wanted.put(a.getId(), 2);
        wanted.put(full.getId(), 1);

        assertFalse(inventoryService.tryReserveAll(wanted));
        assertEquals(3, a.getReservedCount()); // unchanged: no partial reservation
        verify(ticketTypeRepository, never()).save(any());
    }
}
