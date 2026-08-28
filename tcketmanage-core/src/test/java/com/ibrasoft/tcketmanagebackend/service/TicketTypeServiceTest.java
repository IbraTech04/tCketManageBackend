package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the capacity invariant on the ticket-type admin API:
 *
 * <ul>
 *   <li><strong>Capacity is reachable through the API.</strong> {@code POST
 *       /events/{id}/ticket-types} must persist the requested {@code capacity}. Before this was
 *       wired up, every type created that way stored {@code capacity = null} — unlimited — so
 *       {@code InventoryService}'s {@code capacity IS NULL OR ...} reserve guard matched
 *       unconditionally and the oversell mechanism was inert for those types.</li>
 *   <li><strong>{@code capacity >= reservedCount} always holds.</strong> An update that would drop
 *       the cap below the seats already held by live orders is refused with a
 *       {@link ConflictException} (HTTP 409) rather than persisted, since the seats cannot be
 *       un-sold and the resulting row is one the V2 {@code ck_ticket_types_reserved_within_capacity}
 *       CHECK constraint would refuse to store anyway.</li>
 *   <li><strong>{@code null} still means unlimited</strong> on both create and update.</li>
 *   <li><strong>The edit is serialized against the counter.</strong> The capacity check reads
 *       {@code reservedCount} from a row loaded via {@code findByIdForUpdate}, not
 *       {@code findById} — see docs/LOCKING.MD, "Edits that race the counter".</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketTypeServiceTest {

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TicketTypeService ticketTypeService;

    private UUID eventId;
    private UUID ticketTypeId;
    private Event event;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        event = Event.builder()
                .id(eventId)
                .name("Test Event")
                .location("Hall A")
                .description("desc")
                .build();
    }

    private CreateTicketTypeRequest createRequest(Integer capacity) {
        CreateTicketTypeRequest request = new CreateTicketTypeRequest();
        request.setName("General");
        request.setPrice(new BigDecimal("25.00"));
        request.setCapacity(capacity);
        return request;
    }

    /**
     * An update body that carries an explicit {@code capacity} property — including an explicit
     * {@code null}, which means "remove the cap". Calling the setter is exactly what Jackson does
     * for a property present in the JSON, so this faithfully models a body that names the field.
     */
    private UpdateTicketTypeRequest updateRequest(Integer capacity) {
        UpdateTicketTypeRequest request = updateRequestWithoutCapacity();
        request.setCapacity(capacity);
        return request;
    }

    /**
     * An update body with no {@code capacity} property at all — what every client written before
     * the field existed sends. Jackson never calls the setter for an absent property, so the
     * presence flag stays false.
     */
    private UpdateTicketTypeRequest updateRequestWithoutCapacity() {
        UpdateTicketTypeRequest request = new UpdateTicketTypeRequest();
        request.setName("General");
        request.setPrice(new BigDecimal("25.00"));
        return request;
    }

    private TicketType existingType(Integer capacity, int reservedCount) {
        return TicketType.builder()
                .id(ticketTypeId)
                .event(event)
                .name("General")
                .price(new BigDecimal("25.00"))
                .capacity(capacity)
                .reservedCount(reservedCount)
                .entitlements(new ArrayList<>())
                .build();
    }

    // --- create -------------------------------------------------------------------------------

    @Test
    void createTicketType_persistsRequestedCapacity() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketType saved = ticketTypeService.createTicketType(eventId, createRequest(150));

        assertEquals(150, saved.getCapacity(),
                "capacity from the request must reach the entity, or the reserve guard is vacuous");
        assertEquals(0, saved.getReservedCount(), "a new type starts with no seats held");
    }

    @Test
    void createTicketType_nullCapacityMeansUnlimited() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketType saved = ticketTypeService.createTicketType(eventId, createRequest(null));

        assertNull(saved.getCapacity(), "null must stay null — it is the documented 'unlimited'");
    }

    @Test
    void createTicketType_zeroCapacityIsPersistedNotTreatedAsAbsent() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketType saved = ticketTypeService.createTicketType(eventId, createRequest(0));

        // 0 is a real cap ("sold out before it opened"), not a synonym for unlimited. Conflating
        // the two is the classic way a capacity field becomes silently unenforceable.
        assertEquals(0, saved.getCapacity());
    }

    @Test
    void createTicketType_unknownEventIsRejected() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ticketTypeService.createTicketType(eventId, createRequest(10)));
        verify(ticketTypeRepository, never()).save(any(TicketType.class));
    }

    // --- update -------------------------------------------------------------------------------

    @Test
    void updateTicketType_raisingCapacityAboveReservedCountIsApplied() {
        TicketType existing = existingType(100, 40);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketType updated = ticketTypeService.updateTicketType(ticketTypeId, updateRequest(200));

        assertEquals(200, updated.getCapacity());
        assertEquals(40, updated.getReservedCount(), "the edit must not touch the seat counter");
    }

    @Test
    void updateTicketType_loweringCapacityToExactlyReservedCountIsAllowed() {
        TicketType existing = existingType(100, 40);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        // The invariant is reservedCount <= capacity, so equality is the boundary and must pass:
        // this is how an operator closes a type off at exactly what has already been sold.
        TicketType updated = ticketTypeService.updateTicketType(ticketTypeId, updateRequest(40));

        assertEquals(40, updated.getCapacity());
    }

    @Test
    void updateTicketType_loweringCapacityBelowReservedCountIsRejected() {
        TicketType existing = existingType(100, 40);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> ticketTypeService.updateTicketType(ticketTypeId, updateRequest(39)));

        assertTrue(thrown.getMessage().contains("40"),
                "the operator needs to be told how many seats are already held: " + thrown.getMessage());
        assertEquals(100, existing.getCapacity(), "the rejected edit must not be partially applied");
        assertEquals(40, existing.getReservedCount());
        verify(ticketTypeRepository, never()).save(any(TicketType.class));
        // Nothing may be flushed either: a partially-mutated managed entity would still be written
        // out at transaction commit even though the request was refused.
        verify(entityManager, never()).flush();
    }

    @Test
    void updateTicketType_capacityFromUnlimitedToBelowReservedCountIsRejected() {
        // The type had no cap and sold 12 seats; capping it at 5 now is still an oversell.
        TicketType existing = existingType(null, 12);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> ticketTypeService.updateTicketType(ticketTypeId, updateRequest(5)));
        assertNull(existing.getCapacity());
    }

    @Test
    void updateTicketType_nullCapacityClearsTheCapEvenWithSeatsHeld() {
        TicketType existing = existingType(50, 50);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        // Removing the cap widens the allowed set, so it can never break reservedCount <= capacity.
        TicketType updated = ticketTypeService.updateTicketType(ticketTypeId, updateRequest(null));

        assertNull(updated.getCapacity());
    }

    @Test
    void updateTicketType_omittedCapacityLeavesTheStoredCapUntouched() {
        TicketType existing = existingType(50, 50);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        // SECURITY: this is the regression that matters most. A client written before `capacity`
        // existed on this DTO — i.e. every client that exists today — sends no capacity key at all.
        // If that were read as "set capacity to null", renaming a sold-out type would uncap it and
        // the reserve guard's `capacity IS NULL` branch would start matching unconditionally.
        UpdateTicketTypeRequest request = updateRequestWithoutCapacity();
        request.setName("General Admission");

        TicketType updated = ticketTypeService.updateTicketType(ticketTypeId, request);

        assertEquals(50, updated.getCapacity(), "an absent capacity must not uncap a live type");
        assertEquals("General Admission", updated.getName(), "the rest of the edit still applies");
    }

    @Test
    void updateTicketType_omittedCapacityIsNotCheckedAgainstReservedCount() {
        // A body with no capacity key changes nothing about the cap, so it can neither break the
        // invariant nor be rejected for it — even on a type that is exactly full.
        TicketType existing = existingType(10, 10);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketType updated = ticketTypeService.updateTicketType(
                ticketTypeId, updateRequestWithoutCapacity());

        assertEquals(10, updated.getCapacity());
        assertEquals(10, updated.getReservedCount());
    }

    @Test
    void updateRequest_distinguishesAbsentCapacityFromExplicitNull() {
        // Guards the mechanism the two tests above depend on: the hand-written setter is what marks
        // the field present, so a Lombok-generated setter slipping back in would silently turn
        // "absent" into "explicit null" and re-open the uncapping hole.
        assertFalse(updateRequestWithoutCapacity().isCapacityPresent(),
                "a body that never names capacity must not count as present");
        assertTrue(updateRequest(null).isCapacityPresent(),
                "an explicit null capacity must count as present so it can clear the cap");
        assertTrue(updateRequest(25).isCapacityPresent());
    }

    @Test
    void updateTicketType_readsReservedCountUnderTheRowLock() {
        TicketType existing = existingType(100, 40);
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketTypeService.updateTicketType(ticketTypeId, updateRequest(200));

        // SECURITY: the capacity check is only meaningful if the reservedCount it compares against
        // cannot change between the read and the flush. An unlocked findById would let a concurrent
        // reserve slip in after the check and leave capacity < reservedCount committed.
        verify(ticketTypeRepository).findByIdForUpdate(ticketTypeId);
        verify(ticketTypeRepository, never()).findById(any(UUID.class));
    }

    @Test
    void updateTicketType_unknownIdIsRejected() {
        when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> ticketTypeService.updateTicketType(ticketTypeId, updateRequest(10)));
    }
}
