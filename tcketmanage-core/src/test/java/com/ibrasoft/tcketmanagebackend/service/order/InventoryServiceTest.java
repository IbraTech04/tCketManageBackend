package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Capacity accounting is delegated to atomic conditional UPDATEs on the repository (the check and
 * the increment execute in one SQL statement), so these tests verify the service drives those
 * statements correctly: ordering, compensation, clamping, and error translation.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    // Fixed ids with a known sort order, so tests can assert the UUID-ordered lock acquisition.
    private static final UUID ID_LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserve_withinCapacity_singleAtomicUpdate() {
        when(ticketTypeRepository.reserveSeats(ID_LOW, 2)).thenReturn(1);

        inventoryService.reserve(ID_LOW, 2);

        verify(ticketTypeRepository).reserveSeats(ID_LOW, 2);
        verifyNoMoreInteractions(ticketTypeRepository);
    }

    @Test
    void reserve_beyondCapacity_throwsConflictWithAvailability() {
        when(ticketTypeRepository.reserveSeats(ID_LOW, 2)).thenReturn(0);
        when(ticketTypeRepository.findById(ID_LOW)).thenReturn(Optional.of(
                TicketType.builder().id(ID_LOW).name("GA").capacity(5).reservedCount(4).build()));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> inventoryService.reserve(ID_LOW, 2));

        assertTrue(ex.getMessage().contains("requested 2, available 1"));
    }

    @Test
    void reserve_unknownTicketType_throwsNotFound() {
        when(ticketTypeRepository.reserveSeats(ID_LOW, 1)).thenReturn(0);
        when(ticketTypeRepository.findById(ID_LOW)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.reserve(ID_LOW, 1));
    }

    @Test
    void release_normalPath_singleAtomicUpdate() {
        when(ticketTypeRepository.releaseSeats(ID_LOW, 3)).thenReturn(1);

        inventoryService.release(ID_LOW, 3);

        verify(ticketTypeRepository).releaseSeats(ID_LOW, 3);
        verify(ticketTypeRepository, never()).releaseSeatsClamped(any(), anyInt());
    }

    @Test
    void release_wouldUnderflow_clampsAtomically() {
        when(ticketTypeRepository.releaseSeats(ID_LOW, 3)).thenReturn(0);
        when(ticketTypeRepository.existsById(ID_LOW)).thenReturn(true);

        inventoryService.release(ID_LOW, 3);

        verify(ticketTypeRepository).releaseSeatsClamped(ID_LOW, 3);
    }

    @Test
    void release_unknownTicketType_throwsNotFound() {
        when(ticketTypeRepository.releaseSeats(ID_LOW, 1)).thenReturn(0);
        when(ticketTypeRepository.existsById(ID_LOW)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.release(ID_LOW, 1));
        verify(ticketTypeRepository, never()).releaseSeatsClamped(any(), anyInt());
    }

    @Test
    void releaseAll_releasesEveryTypeInUuidOrder() {
        when(ticketTypeRepository.releaseSeats(any(UUID.class), anyInt())).thenReturn(1);

        // Supply keys in reverse order; the service must still release low-UUID first.
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        quantities.put(ID_HIGH, 1);
        quantities.put(ID_LOW, 2);

        inventoryService.releaseAll(quantities);

        InOrder inOrder = inOrder(ticketTypeRepository);
        inOrder.verify(ticketTypeRepository).releaseSeats(ID_LOW, 2);
        inOrder.verify(ticketTypeRepository).releaseSeats(ID_HIGH, 1);
    }

    @Test
    void tryReserveAll_allFit_reservesEveryTypeInUuidOrderAndReturnsTrue() {
        when(ticketTypeRepository.reserveSeats(any(UUID.class), anyInt())).thenReturn(1);

        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        wanted.put(ID_HIGH, 1);
        wanted.put(ID_LOW, 2);

        assertTrue(inventoryService.tryReserveAll(wanted));

        InOrder inOrder = inOrder(ticketTypeRepository);
        inOrder.verify(ticketTypeRepository).reserveSeats(ID_LOW, 2);
        inOrder.verify(ticketTypeRepository).reserveSeats(ID_HIGH, 1);
        verify(ticketTypeRepository, never()).releaseSeats(any(), anyInt());
    }

    @Test
    void tryReserveAll_laterTypeSoldOut_compensatesEarlierReservationsAndReturnsFalse() {
        when(ticketTypeRepository.reserveSeats(ID_LOW, 2)).thenReturn(1);
        when(ticketTypeRepository.reserveSeats(ID_HIGH, 1)).thenReturn(0); // sold out

        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        wanted.put(ID_LOW, 2);
        wanted.put(ID_HIGH, 1);

        assertFalse(inventoryService.tryReserveAll(wanted));

        // The increment already applied to the first type must be explicitly undone (no rollback:
        // the caller's transaction still has to commit its own status change).
        verify(ticketTypeRepository).releaseSeats(ID_LOW, 2);
    }

    @Test
    void tryReserveAll_firstTypeSoldOut_nothingToCompensate() {
        when(ticketTypeRepository.reserveSeats(ID_LOW, 2)).thenReturn(0);

        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        wanted.put(ID_LOW, 2);
        wanted.put(ID_HIGH, 1);

        assertFalse(inventoryService.tryReserveAll(wanted));

        verify(ticketTypeRepository, never()).reserveSeats(eq(ID_HIGH), anyInt());
        verify(ticketTypeRepository, never()).releaseSeats(any(), anyInt());
    }

    @Test
    void seatsByTicketType_groupsAndSumsItems() {
        TicketType low = TicketType.builder().id(ID_LOW).name("GA").build();
        TicketType high = TicketType.builder().id(ID_HIGH).name("VIP").build();
        List<OrderItem> items = List.of(
                OrderItem.builder().ticketType(low).build(),
                OrderItem.builder().ticketType(high).build(),
                OrderItem.builder().ticketType(low).build());

        Map<UUID, Integer> seats = InventoryService.seatsByTicketType(items);

        assertEquals(Map.of(ID_LOW, 2, ID_HIGH, 1), seats);
    }
}
