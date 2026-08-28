package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketRequest;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * The invariant under test: an {@code ACTIVE} ticket row and a unit of {@code reserved_count} are
 * created, destroyed, and moved together. Issuing a comp ticket reserves a seat, deleting one
 * releases it, and a type change transfers it — all through {@link InventoryService}'s atomic
 * conditional UPDATEs, in ticket-type-UUID order (docs/LOCKING.MD), with a sold-out target
 * rolling the whole update back rather than stranding or duplicating a seat.
 *
 * <p>Also covers the two authorization/data-integrity holes {@code updateTicket} used to have: no
 * event-ownership check on the incoming {@code ticketTypeId} (a cross-event rebind inherits the
 * other event's zone entitlements) and unconditional overwrites of the holder fields (a partial
 * update wiped holder data, and a swapped email redirects the signed-QR resend).
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    // Fixed ids with a known sort order, so tests can assert UUID-ordered lock acquisition.
    private static final UUID TYPE_LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TYPE_HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock private TicketRepository ticketRepository;
    @Mock private ScanEventRepository scanEventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private TicketService ticketService;

    private Event testEvent;
    private Event otherEvent;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .time(OffsetDateTime.now())
                .location("Test Location")
                .description("Test Description")
                .build();
        otherEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Other Event")
                .time(OffsetDateTime.now())
                .location("Elsewhere")
                .description("A different event")
                .build();
    }

    private TicketType type(UUID id, String name, Event event) {
        return TicketType.builder().id(id).event(event).name(name).build();
    }

    private Ticket activeTicket(UUID id, TicketType ticketType) {
        return Ticket.builder().ID(id).firstName("John").lastName("Doe")
                .email("john.doe@example.com").event(testEvent).ticketType(ticketType)
                .status(TicketStatus.ACTIVE).build();
    }

    // ---------------------------------------------------------------- createTicket

    @Test
    void testCreateTicket_Success() {
        UUID eventId = testEvent.getId();
        TicketType ticketType = type(TYPE_LOW, "General", testEvent);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(ticketTypeRepository.findById(TYPE_LOW)).thenReturn(Optional.of(ticketType));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = ticketService.createTicket("John", "Doe", "john.doe@example.com", eventId, TYPE_LOW);

        assertNotNull(created.getID());
        assertEquals("John", created.getFirstName());
        assertEquals(testEvent, created.getEvent());
        assertEquals(ticketType, created.getTicketType());
        assertEquals(TicketStatus.ACTIVE, created.getStatus());

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("john.doe@example.com", captor.getValue().getEmail());
    }

    @Test
    void createTicket_reservesOneSeatBeforePersisting() {
        UUID eventId = testEvent.getId();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(ticketTypeRepository.findById(TYPE_LOW))
                .thenReturn(Optional.of(type(TYPE_LOW, "General", testEvent)));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.createTicket("John", "Doe", "john.doe@example.com", eventId, TYPE_LOW);

        // Reserve first: a sold-out type must fail before any ticket row exists.
        InOrder inOrder = inOrder(inventoryService, ticketRepository);
        inOrder.verify(inventoryService).reserve(TYPE_LOW, 1);
        inOrder.verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicket_soldOutType_propagatesConflictAndPersistsNothing() {
        UUID eventId = testEvent.getId();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(ticketTypeRepository.findById(TYPE_LOW))
                .thenReturn(Optional.of(type(TYPE_LOW, "General", testEvent)));
        doThrow(new ConflictException("sold out")).when(inventoryService).reserve(TYPE_LOW, 1);

        assertThrows(ConflictException.class,
                () -> ticketService.createTicket("John", "Doe", "john.doe@example.com", eventId, TYPE_LOW));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void createTicket_typeFromAnotherEvent_rejectedWithoutReserving() {
        UUID eventId = testEvent.getId();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(testEvent));
        when(ticketTypeRepository.findById(TYPE_HIGH))
                .thenReturn(Optional.of(type(TYPE_HIGH, "Foreign VIP", otherEvent)));

        assertThrows(IllegalArgumentException.class,
                () -> ticketService.createTicket("John", "Doe", "john@example.com", eventId, TYPE_HIGH));

        verifyNoInteractions(inventoryService);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // ---------------------------------------------------------------- updateTicket: holder fields

    @Test
    void updateTicket_nullHolderFields_leaveStoredValuesIntact() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        // Body carries nothing at all — the previous implementation nulled all three fields.
        Ticket updated = ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, null));

        assertEquals("John", updated.getFirstName());
        assertEquals("Doe", updated.getLastName());
        assertEquals("john.doe@example.com", updated.getEmail());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateTicket_suppliedHolderFields_areTrimmedAndApplied() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        // The DTO normalises on binding, so its own @Email / @Size constraints judge the value that
        // will actually be stored rather than a whitespace-padded one that @Email would reject.
        UpdateTicketRequest request = new UpdateTicketRequest("  Jane  ", null, " jane@example.com ", null);
        assertEquals("Jane", request.getFirstName());
        assertEquals("jane@example.com", request.getEmail());

        Ticket updated = ticketService.updateTicket(ticketId, request);

        assertEquals("Jane", updated.getFirstName());
        assertEquals("Doe", updated.getLastName()); // untouched
        assertEquals("jane@example.com", updated.getEmail());
    }

    @Test
    void updateTicket_readsTheTicketUnderItsRowLock() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        TicketType target = type(TYPE_HIGH, "VIP", testEvent);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH)).thenReturn(Optional.of(target));
        when(ticketRepository.save(existing)).thenReturn(existing);

        ticketService.updateTicket(ticketId, new UpdateTicketRequest(null, null, null, TYPE_HIGH));

        // status and ticketType decide which seats move, so they have to be read inside the lock:
        // two concurrent transfers off the same type would otherwise both release it.
        verify(ticketRepository).findByIdForUpdate(ticketId);
        verify(ticketRepository, never()).findById(any(UUID.class));
    }

    @Test
    void updateTicket_nullStatusTicket_stillTransfersItsSeat() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        existing.setStatus(null);
        TicketType target = type(TYPE_HIGH, "VIP", testEvent);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH)).thenReturn(Optional.of(target));
        when(ticketRepository.save(existing)).thenReturn(existing);

        ticketService.updateTicket(ticketId, new UpdateTicketRequest(null, null, null, TYPE_HIGH));

        // Otherwise the ticket moves but its seat does not: the old type stays over-reserved and
        // the new one under-reserved, forever.
        verify(inventoryService).release(TYPE_LOW, 1);
        verify(inventoryService).reserve(TYPE_HIGH, 1);
    }

    @Test
    void updateTicket_unknownTicket_throwsNotFound() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ticketService.updateTicket(ticketId, new UpdateTicketRequest(null, null, null, null)));
    }

    // ---------------------------------------------------------------- updateTicket: type change

    @Test
    void updateTicket_typeFromAnotherEvent_rejectedAndInventoryUntouched() {
        UUID ticketId = UUID.randomUUID();
        TicketType currentType = type(TYPE_LOW, "General", testEvent);
        Ticket existing = activeTicket(ticketId, currentType);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH))
                .thenReturn(Optional.of(type(TYPE_HIGH, "Foreign VIP", otherEvent)));

        assertThrows(IllegalArgumentException.class, () -> ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, TYPE_HIGH)));

        // The rebind that would have inherited the other event's zone entitlements never happened.
        assertEquals(currentType, existing.getTicketType());
        verifyNoInteractions(inventoryService);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updateTicket_sameTicketType_movesNoInventory() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketRepository.save(existing)).thenReturn(existing);

        ticketService.updateTicket(ticketId, new UpdateTicketRequest(null, null, null, TYPE_LOW));

        // Short-circuited before the type is even re-loaded: no inventory movement, and no attempt
        // to release and re-reserve the same row inside one transaction.
        verifyNoInteractions(inventoryService);
        verify(ticketTypeRepository, never()).findById(any());
    }

    @Test
    void updateTicket_typeChangeUpwards_releasesLowThenReservesHigh() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        TicketType target = type(TYPE_HIGH, "VIP", testEvent);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH)).thenReturn(Optional.of(target));
        when(ticketRepository.save(existing)).thenReturn(existing);

        Ticket updated = ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, TYPE_HIGH));

        assertEquals(target, updated.getTicketType());
        // Ticket-type rows are touched in UUID order, per docs/LOCKING.MD.
        InOrder inOrder = inOrder(inventoryService);
        inOrder.verify(inventoryService).release(TYPE_LOW, 1);
        inOrder.verify(inventoryService).reserve(TYPE_HIGH, 1);
    }

    @Test
    void updateTicket_typeChangeDownwards_stillTouchesRowsInUuidOrder() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_HIGH, "VIP", testEvent));
        TicketType target = type(TYPE_LOW, "General", testEvent);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_LOW)).thenReturn(Optional.of(target));
        when(ticketRepository.save(existing)).thenReturn(existing);

        ticketService.updateTicket(ticketId, new UpdateTicketRequest(null, null, null, TYPE_LOW));

        // Transferring the other way must NOT flip to "release high, reserve low": two operators
        // swapping tickets between the same pair of types would then deadlock on each other's rows.
        InOrder inOrder = inOrder(inventoryService);
        inOrder.verify(inventoryService).reserve(TYPE_LOW, 1);
        inOrder.verify(inventoryService).release(TYPE_HIGH, 1);
    }

    @Test
    void updateTicket_targetTypeSoldOut_propagatesConflictSoTheReleaseRollsBack() {
        UUID ticketId = UUID.randomUUID();
        TicketType currentType = type(TYPE_LOW, "General", testEvent);
        Ticket existing = activeTicket(ticketId, currentType);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH))
                .thenReturn(Optional.of(type(TYPE_HIGH, "VIP", testEvent)));
        doThrow(new ConflictException("sold out")).when(inventoryService).reserve(TYPE_HIGH, 1);

        assertThrows(ConflictException.class, () -> ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, TYPE_HIGH)));

        // Deliberately NOT compensated in Java (contrast InventoryService.tryReserveAll): letting
        // the ConflictException escape marks the transaction rollback-only, which undoes the
        // release of the old type too, so the ticket keeps the seat it already held. Nothing here
        // needs to survive the rollback, so there is nothing left to save.
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(inventoryService, never()).release(TYPE_HIGH, 1);
    }

    @Test
    void updateTicket_cancelledTicket_changesTypeWithoutMovingInventory() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        existing.setStatus(TicketStatus.CANCELLED);
        TicketType target = type(TYPE_HIGH, "VIP", testEvent);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH)).thenReturn(Optional.of(target));
        when(ticketRepository.save(existing)).thenReturn(existing);

        Ticket updated = ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, TYPE_HIGH));

        // A cancelled ticket holds no seat: nothing to give back, and reserving one on the new type
        // would conjure a seat no live ticket occupies.
        assertEquals(target, updated.getTicketType());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void updateTicket_unknownTargetType_throwsNotFound() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findById(TYPE_HIGH)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ticketService.updateTicket(ticketId,
                new UpdateTicketRequest(null, null, null, TYPE_HIGH)));

        verifyNoInteractions(inventoryService);
    }

    // ---------------------------------------------------------------- deleteTicket

    @Test
    void deleteTicket_activeTicket_releasesItsSeat() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));

        assertTrue(ticketService.deleteTicket(ticketId));

        verify(inventoryService).release(TYPE_LOW, 1);
        verify(ticketRepository).delete(existing);
    }

    @Test
    void deleteTicket_cancelledTicket_deletesWithoutDoubleReleasing() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        existing.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));

        assertTrue(ticketService.deleteTicket(ticketId));

        // Its seat went back when it was cancelled; releasing again would oversell by one.
        verify(inventoryService, never()).release(any(), anyInt());
        verify(ticketRepository).delete(existing);
    }

    @Test
    void deleteTicket_nullStatusTicket_stillReleasesItsSeat() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        existing.setStatus(null); // the status column is nullable and has no DB default
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));

        assertTrue(ticketService.deleteTicket(ticketId));

        // A row that was never stamped ACTIVE is still live and still holds a seat. Testing for
        // "== ACTIVE" here would silently shrink the type's capacity by one, permanently.
        verify(inventoryService).release(TYPE_LOW, 1);
    }

    @Test
    void deleteTicket_readsTheTicketUnderItsRowLock() {
        UUID ticketId = UUID.randomUUID();
        Ticket existing = activeTicket(ticketId, type(TYPE_LOW, "General", testEvent));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(existing));

        ticketService.deleteTicket(ticketId);

        // An unlocked read lets a concurrent type change commit in between, sending the seat back
        // to a type this ticket no longer sits on.
        verify(ticketRepository).findByIdForUpdate(ticketId);
        verify(ticketRepository, never()).findById(any(UUID.class));
    }

    @Test
    void deleteTicket_unknownTicket_returnsFalseAndTouchesNothing() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.empty());

        assertFalse(ticketService.deleteTicket(ticketId));

        verifyNoInteractions(inventoryService);
        verify(ticketRepository, never()).delete(any(Ticket.class));
    }

    // ---------------------------------------------------------------- reads

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
