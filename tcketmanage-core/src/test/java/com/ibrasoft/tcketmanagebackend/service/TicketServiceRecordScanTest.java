package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceRecordScanTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ScanEventRepository scanEventRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ScanEventService scanEventService;

    @InjectMocks
    private TicketService ticketService;

    private Ticket mockTicket;
    private Zone mockZone;
    private Event mockEvent;
    private UUID ticketId;
    private UUID zoneId;

    @BeforeEach
    void setUp() {
        ticketId = UUID.randomUUID();
        zoneId = UUID.randomUUID();

        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Tech Conference 2025")
                .location("Convention Center")
                .description("Annual technology conference")
                .time(OffsetDateTime.of(2025, 6, 15, 9, 0, 0, 0, ZoneOffset.UTC))
                .build();

        mockZone = Zone.builder()
                .id(zoneId)
                .name("Main Entrance")
                
                .event(mockEvent)
                .build();

        TicketType mockTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .event(mockEvent)
                .name("VIP")
                .build();

        mockTicket = Ticket.builder()
                .ID(ticketId)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .event(mockEvent)
                .ticketType(mockTicketType)
                .build();
    }

    @Test
    void testRecordTicketScan_SavesScanEventWithTicketAndZone() {
        ticketService.recordTicketScan(mockTicket, mockZone);

        ArgumentCaptor<ScanEvent> captor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(1)).save(captor.capture());

        ScanEvent captured = captor.getValue();
        assertNotNull(captured.getId(), "Surrogate id should be set");
        assertEquals(ticketId, captured.getTicketId());
        assertEquals(mockZone, captured.getZone());
        assertNotNull(captured.getTimestamp());
    }

    @Test
    void testRecordTicketScan_MultipleZones_RecordsEach() {
        Zone libraryZone = Zone.builder()
                .id(UUID.randomUUID())
                .name("Library Zone")
                
                .event(mockEvent)
                .build();

        ticketService.recordTicketScan(mockTicket, mockZone);
        ticketService.recordTicketScan(mockTicket, libraryZone);

        ArgumentCaptor<ScanEvent> captor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(2)).save(captor.capture());

        var captured = captor.getAllValues();
        assertEquals(mockZone, captured.get(0).getZone());
        assertEquals(libraryZone, captured.get(1).getZone());
        assertEquals(ticketId, captured.get(0).getTicketId());
        assertEquals(ticketId, captured.get(1).getTicketId());
    }

    @Test
    void testRecordTicketScan_WithNullTicketType_StillRecords() {
        Ticket ticketWithoutType = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("NoType")
                .email("john@example.com")
                .event(mockEvent)
                .ticketType(null)
                .build();

        assertDoesNotThrow(() -> ticketService.recordTicketScan(ticketWithoutType, mockZone));

        ArgumentCaptor<ScanEvent> captor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(1)).save(captor.capture());
        assertEquals(ticketWithoutType.getID(), captor.getValue().getTicketId());
        assertEquals(mockZone, captor.getValue().getZone());
    }
}
