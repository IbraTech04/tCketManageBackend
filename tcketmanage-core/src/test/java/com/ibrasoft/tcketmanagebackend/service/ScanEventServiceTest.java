package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanOutcome;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ValidationResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneEntitlementRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import com.ibrasoft.tcketmanagebackend.service.ticket.ScanEventService;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Covers the admission invariants of the entry path: only an ACTIVE ticket may pass, on
 * <em>both</em> the recording ({@code scanTicket}/{@code scanByQr}) and the advisory
 * ({@code validateTicketForZone}) endpoints; a signed QR is honoured only against the event it was
 * minted for; and zone entitlement plus the per-zone entry limit still gate everything else.
 */
@ExtendWith(MockitoExtension.class)
class ScanEventServiceTest {

    @Mock
    private ScanEventRepository scanEventRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ZoneEntitlementRepository entitlementRepository;
    @Mock
    private CryptoService cryptoService;

    @InjectMocks
    private ScanEventService scanEventService;

    private UUID ticketId;
    private UUID zoneId;
    private UUID typeId;
    private UUID eventId;
    private Ticket ticket;
    private Zone zone;
    private TicketType ticketType;
    private Event event;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
        typeId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        event = Event.builder().id(eventId).name("Main Event").build();
        ticketType = TicketType.builder().id(typeId).name("VIP").build();
        // No explicit status: Ticket defaults to ACTIVE, which is what the happy paths assume.
        ticket = Ticket.builder().ID(ticketId).firstName("A").lastName("B").email("a@b.com")
                .event(event).ticketType(ticketType).build();
        zone = Zone.builder().id(zoneId).name("Main").build();

        // Scans take a pessimistic write lock on the ticket row (findByIdForUpdate); read-only
        // validation uses a plain find (no lock is allowed in a read-only transaction).
        lenient().when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        lenient().when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        lenient().when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
    }

    private ZoneEntitlement entitlement(Integer maxEntries) {
        return ZoneEntitlement.builder().ticketType(ticketType).zone(zone).maxEntries(maxEntries).build();
    }

    @Test
    void scan_deniedWhenNoEntitlement() {
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.empty());

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertEquals(ScanOutcome.NO_ZONE_ENTITLEMENT, result.getOutcome());
        verify(scanEventRepository, never()).save(any());
    }

    @Test
    void scan_succeedsBelowLimit() {
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(3)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(1);
        when(scanEventRepository.save(any(ScanEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertEquals(ScanOutcome.SUCCESS, result.getOutcome());
        assertNotNull(result.getScanEvent());
        verify(scanEventRepository, times(1)).save(any(ScanEvent.class));
    }

    @Test
    void scan_deniedAtLimit() {
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(2)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(2);

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertEquals(ScanOutcome.ENTRY_LIMIT_REACHED, result.getOutcome());
        verify(scanEventRepository, never()).save(any());
    }

    @Test
    void validate_reportsRemainingEntries() {
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(3)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(1);

        ValidationResult result = scanEventService.validateTicketForZone(ticketId, zoneId);

        assertTrue(result.isValid());
    }

    @Test
    void scan_succeedsWithUnlimitedEntitlement() {
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(null)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(99);
        when(scanEventRepository.save(any(ScanEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertEquals(ScanOutcome.SUCCESS, result.getOutcome());
        verify(scanEventRepository, times(1)).save(any(ScanEvent.class));
    }

    @Test
    void scan_ticketNotFound_throws() {
        UUID missing = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scanEventService.scanTicket(missing, zoneId));
    }

    @Test
    void scanByQr_validSignature_scans() throws Exception {
        // The token's eventID matches the ticket's event, which is the only case that may scan.
        TicketQRData data = TicketQRData.builder().ticketID(ticketId).eventID(eventId).build();
        when(cryptoService.verify("payload")).thenReturn(data);
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(3)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(0);
        when(scanEventRepository.save(any(ScanEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ScanResult result = scanEventService.scanByQr("payload", zoneId);

        assertEquals(ScanOutcome.SUCCESS, result.getOutcome());
        verify(scanEventRepository, times(1)).save(any(ScanEvent.class));
    }

    @Test
    void scanByQr_invalidSignature_denied() throws Exception {
        when(cryptoService.verify("payload")).thenThrow(new SecurityException("Invalid signature"));

        ScanResult result = scanEventService.scanByQr("payload", zoneId);

        assertEquals(ScanOutcome.INVALID_QR, result.getOutcome());
        verify(scanEventRepository, never()).save(any());
    }

    /**
     * The signed payload's eventID must be checked against the ticket, not merely decoded. A token
     * minted for a different event is a token that no longer describes the ticket it names.
     */
    @Test
    void scanByQr_eventIdDoesNotMatchTicketEvent_denied() throws Exception {
        TicketQRData data = TicketQRData.builder().ticketID(ticketId).eventID(UUID.randomUUID()).build();
        when(cryptoService.verify("payload")).thenReturn(data);

        ScanResult result = scanEventService.scanByQr("payload", zoneId);

        assertEquals(ScanOutcome.INVALID_QR, result.getOutcome());
        // Rejected before any entitlement work happens at all.
        verify(entitlementRepository, never()).findByTicketType_IdAndZone_Id(any(), any());
        verify(scanEventRepository, never()).save(any());
    }

    /** A ticket that has lost its event has lost the thing its token was bound to: deny, not skip. */
    @Test
    void scanByQr_ticketHasNoEvent_denied() throws Exception {
        ticket.setEvent(null);
        TicketQRData data = TicketQRData.builder().ticketID(ticketId).eventID(eventId).build();
        when(cryptoService.verify("payload")).thenReturn(data);

        ScanResult result = scanEventService.scanByQr("payload", zoneId);

        assertEquals(ScanOutcome.INVALID_QR, result.getOutcome());
        verify(scanEventRepository, never()).save(any());
    }

    /**
     * The manual/operator path carries no token, so there is no event binding to enforce — it must
     * not be collaterally denied by the QR check.
     */
    @Test
    void scanTicket_byIdWithoutToken_isNotSubjectToEventBinding() {
        ticket.setEvent(null);
        when(entitlementRepository.findByTicketType_IdAndZone_Id(typeId, zoneId)).thenReturn(Optional.of(entitlement(3)));
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId)).thenReturn(0);
        when(scanEventRepository.save(any(ScanEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertEquals(ScanOutcome.SUCCESS, result.getOutcome());
    }

    @Test
    void scan_deniedWhenTicketCancelled() {
        ticket.setStatus(TicketStatus.CANCELLED);

        ScanResult result = scanEventService.scanTicket(ticketId, zoneId);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("CANCELLED"));
        verify(scanEventRepository, never()).save(any());
    }

    /**
     * The regression this endpoint existed with: /scans/validate answered on entitlement and entry
     * count alone, so a refunded ticket — which keeps its entitlement rows — reported valid=true and
     * any turnstile built on it admitted the holder.
     */
    @Test
    void validate_deniedWhenTicketCancelled() {
        ticket.setStatus(TicketStatus.CANCELLED);

        ValidationResult result = scanEventService.validateTicketForZone(ticketId, zoneId);

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("CANCELLED"),
                "message should name the status so an operator can tell a refund from a wrong zone");
        // Denied on its own merits: entitlement and entry count are never even consulted.
        verify(entitlementRepository, never()).findByTicketType_IdAndZone_Id(any(), any());
        verify(scanEventRepository, never()).countZoneEntriesByTicketId(any(), any());
    }
}
