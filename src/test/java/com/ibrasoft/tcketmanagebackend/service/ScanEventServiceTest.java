package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanOutcome;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ValidationResult;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneEntitlementRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
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
    private Ticket ticket;
    private Zone zone;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
        typeId = UUID.randomUUID();

        ticketType = TicketType.builder().id(typeId).name("VIP").build();
        ticket = Ticket.builder().ID(ticketId).firstName("A").lastName("B").email("a@b.com")
                .ticketType(ticketType).build();
        zone = Zone.builder().id(zoneId).name("Main").build();

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
        when(ticketRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scanEventService.scanTicket(missing, zoneId));
    }

    @Test
    void scanByQr_validSignature_scans() throws Exception {
        TicketQRData data = TicketQRData.builder().ticketID(ticketId).eventID(UUID.randomUUID()).build();
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
}
