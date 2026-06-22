package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ScanEventRepository scanEventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private TicketService ticketService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .time(LocalDateTime.now())
                .location("Test Location")
                .description("Test Description")
                .build();
    }

    @Test
    void testCreateTicket_Success() {
        UUID eventId = testEvent.getId();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType ticketType = TicketType.builder()
                .id(ticketTypeId).event(testEvent).name("General").build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = ticketService.createTicket("John", "Doe", "john.doe@example.com", eventId, ticketTypeId);

        assertNotNull(created.getID());
        assertEquals("John", created.getFirstName());
        assertEquals(testEvent, created.getEvent());
        assertEquals(ticketType, created.getTicketType());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("john.doe@example.com", captor.getValue().getEmail());
    }

    @Test
    void testFindTicketById_Found() {
        UUID id = UUID.randomUUID();
        Ticket t = Ticket.builder().ID(id).firstName("Test").lastName("User")
                .email("test@example.com").event(testEvent).build();
        when(ticketRepository.findById(id)).thenReturn(Optional.of(t));

        assertTrue(ticketService.findTicketById(id).isPresent());
    }

    @Test
    void testFindTicketById_NotFound() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findById(id)).thenReturn(Optional.empty());
        assertTrue(ticketService.findTicketById(id).isEmpty());
    }

    @Test
    void testGetZoneEntryCount_ReturnsCorrectCount() {
        UUID ticketId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        Ticket ticket = Ticket.builder().ID(ticketId).firstName("John").lastName("Doe")
                .email("john@example.com").build();
        Zone zone = Zone.builder().id(zoneId).name("VIP Zone").build();

        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(3);

        assertEquals(3, ticketService.getZoneEntryCount(ticket, zone));
        verify(scanEventRepository).countZoneEntriesByTicketId(ticketId, zoneId);
    }

    // --- revoke / reactivate -----------------------------------------------------------------

    private Ticket orderTicket(TicketStatus status, TicketType ticketType) {
        return Ticket.builder().ID(UUID.randomUUID()).firstName("A").lastName("B")
                .email("a@b.com").event(testEvent).ticketType(ticketType).status(status)
                .order(Order.builder().id(UUID.randomUUID()).build()).build();
    }

    @Test
    void revoke_activeOrderTicket_releasesSeatAndMarksRevoked() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.ACTIVE, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.revokeTicket(t.getID());

        assertEquals(TicketStatus.REVOKED, result.getStatus());
        verify(inventoryService, times(1)).release(type.getId(), 1);
    }

    @Test
    void revoke_compTicket_marksRevokedWithoutTouchingInventory() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        // Comp ticket: no order, so it never consumed a seat.
        Ticket t = Ticket.builder().ID(UUID.randomUUID()).firstName("A").lastName("B")
                .email("a@b.com").event(testEvent).ticketType(type).status(TicketStatus.ACTIVE).build();
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.revokeTicket(t.getID());

        assertEquals(TicketStatus.REVOKED, result.getStatus());
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void revoke_alreadyRevoked_isIdempotentNoOp() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.REVOKED, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));

        Ticket result = ticketService.revokeTicket(t.getID());

        assertEquals(TicketStatus.REVOKED, result.getStatus());
        verify(inventoryService, never()).release(any(), anyInt());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void revoke_cancelledTicket_throws() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.CANCELLED, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));

        assertThrows(ConflictException.class, () -> ticketService.revokeTicket(t.getID()));
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void reactivate_revokedOrderTicket_reReservesAndMarksActive() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.REVOKED, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket result = ticketService.reactivateTicket(t.getID());

        assertEquals(TicketStatus.ACTIVE, result.getStatus());
        verify(inventoryService, times(1)).reserve(type.getId(), 1);
    }

    @Test
    void reactivate_soldOut_throwsAndLeavesTicketRevoked() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.REVOKED, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));
        doThrow(new ConflictException("sold out")).when(inventoryService).reserve(type.getId(), 1);

        assertThrows(ConflictException.class, () -> ticketService.reactivateTicket(t.getID()));
        assertEquals(TicketStatus.REVOKED, t.getStatus());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void reactivate_alreadyActive_isIdempotentNoOp() {
        TicketType type = TicketType.builder().id(UUID.randomUUID()).event(testEvent).name("GA").build();
        Ticket t = orderTicket(TicketStatus.ACTIVE, type);
        when(ticketRepository.findByIdForUpdate(t.getID())).thenReturn(Optional.of(t));

        Ticket result = ticketService.reactivateTicket(t.getID());

        assertEquals(TicketStatus.ACTIVE, result.getStatus());
        verify(inventoryService, never()).reserve(any(), anyInt());
        verify(ticketRepository, never()).save(any());
    }
}
