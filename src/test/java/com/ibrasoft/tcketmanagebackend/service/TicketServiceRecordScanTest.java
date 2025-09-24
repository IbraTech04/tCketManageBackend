package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEventId;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceRecordScanTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ScanEventRepository scanEventRepository;

    @InjectMocks
    private TicketService ticketService;

    private Ticket mockTicket;
    private Zone mockZone;
    private Event mockEvent;
    private TicketType mockTicketType;
    private UUID ticketId;
    private UUID zoneId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        // Create test IDs
        ticketId = UUID.randomUUID();
        zoneId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        // Create mock event
        mockEvent = Event.builder()
                .id(eventId)
                .name("Tech Conference 2025")
                .location("Convention Center")
                .description("Annual technology conference")
                .time(LocalDateTime.of(2025, 6, 15, 9, 0))
                .build();

        // Create mock zone
        mockZone = Zone.builder()
                .id(zoneId)
                .name("Main Entrance")
                .bitPosition(0)
                .event(mockEvent)
                .build();

        // Create mock ticket type - VIP with multiple zone access
        mockTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                .zonePermissions(7L) // Binary: 111 (access to zones 0, 1, 2)
                .build();

        // Create mock ticket
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
    void testRecordTicketScan_VIPTicket_ShouldCreateAndSaveScanEvent() {
        // Arrange
        long beforeScanTime = System.currentTimeMillis();

        // Act
        ticketService.recordTicketScan(mockTicket, mockZone);

        long afterScanTime = System.currentTimeMillis();

        // Assert
        ArgumentCaptor<ScanEvent> scanEventCaptor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(1)).save(scanEventCaptor.capture());

        ScanEvent capturedScanEvent = scanEventCaptor.getValue();

        // Verify the scan event properties
        assertNotNull(capturedScanEvent, "ScanEvent should not be null");
        assertNotNull(capturedScanEvent.getId(), "ScanEvent ID should not be null");
        assertEquals(mockZone, capturedScanEvent.getZone(), "Zone should match the provided zone");

        // Verify the composite ID
        ScanEventId scanEventId = capturedScanEvent.getId();
        assertEquals(ticketId, scanEventId.getTicketId(), "Ticket ID should match");

        // Verify timestamp is within reasonable range
        assertNotNull(scanEventId.getTimestamp(), "Timestamp should not be null");

        long actualTimestamp = scanEventId.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC) * 1000;
        assertTrue(actualTimestamp >= beforeScanTime - 1000,
                "Timestamp should be after or near the scan start time");
        assertTrue(actualTimestamp <= afterScanTime + 1000,
                "Timestamp should be before or near the scan end time");
    }

    @Test
    void testRecordTicketScan_StudentTicketMultipleZones_ShouldCreateMultipleScanEvents() {
        // Arrange - Create a Student ticket type with limited zone access
        TicketType studentTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Student")
                .zonePermissions(1L) // Binary: 001 (access to zone 0 only)
                .build();

        Ticket studentTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Alice")
                .lastName("Student")
                .email("alice@university.edu")
                .event(mockEvent)
                .ticketType(studentTicketType)
                .build();

        Zone libraryZone = Zone.builder()
                .id(UUID.randomUUID())
                .name("Library Zone")
                .bitPosition(1)
                .event(mockEvent)
                .build();

        // Act - Record scans for different zones
        ticketService.recordTicketScan(studentTicket, mockZone); // Zone 0 - should work
        ticketService.recordTicketScan(studentTicket, libraryZone); // Zone 1 - student doesn't have access

        // Assert
        verify(scanEventRepository, times(2)).save(any(ScanEvent.class));

        ArgumentCaptor<ScanEvent> scanEventCaptor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(2)).save(scanEventCaptor.capture());

        var capturedScanEvents = scanEventCaptor.getAllValues();
        assertEquals(2, capturedScanEvents.size(), "Should have captured 2 scan events");

        // Verify both scan events are recorded regardless of permissions (logging all attempts)
        assertEquals(mockZone, capturedScanEvents.get(0).getZone(), "First scan should be for main entrance");
        assertEquals(libraryZone, capturedScanEvents.get(1).getZone(), "Second scan should be for library zone");
        assertEquals(studentTicket.getID(), capturedScanEvents.get(0).getId().getTicketId());
        assertEquals(studentTicket.getID(), capturedScanEvents.get(1).getId().getTicketId());
    }

    @Test
    void testRecordTicketScan_DifferentTicketTypes_ShouldTrackAllAttempts() {
        // Arrange - Create different ticket types
        TicketType[] ticketTypes = {
            TicketType.builder().id(UUID.randomUUID()).name("General").zonePermissions(1L).build(), // Zone 0 only
            TicketType.builder().id(UUID.randomUUID()).name("Premium").zonePermissions(3L).build(), // Zones 0,1
            TicketType.builder().id(UUID.randomUUID()).name("VIP").zonePermissions(15L).build()     // Zones 0,1,2,3
        };

        Ticket[] tickets = new Ticket[3];
        for (int i = 0; i < 3; i++) {
            tickets[i] = Ticket.builder()
                    .ID(UUID.randomUUID())
                    .firstName("User" + (i + 1))
                    .lastName("Test")
                    .email("user" + (i + 1) + "@example.com")
                    .event(mockEvent)
                    .ticketType(ticketTypes[i])
                    .build();
        }

        // Act - Record scans for all ticket types
        for (Ticket ticket : tickets) {
            ticketService.recordTicketScan(ticket, mockZone);
        }

        // Assert
        verify(scanEventRepository, times(3)).save(any(ScanEvent.class));

        ArgumentCaptor<ScanEvent> scanEventCaptor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(3)).save(scanEventCaptor.capture());

        var capturedScanEvents = scanEventCaptor.getAllValues();
        assertEquals(3, capturedScanEvents.size(), "Should have captured 3 scan events");

        // Verify all tickets were scanned and recorded
        for (int i = 0; i < 3; i++) {
            assertEquals(tickets[i].getID(), capturedScanEvents.get(i).getId().getTicketId(),
                    "Scan event " + (i + 1) + " should have correct ticket ID");
            assertEquals(mockZone, capturedScanEvents.get(i).getZone(),
                    "All scans should be for the same zone");
        }
    }

    @Test
    void testRecordTicketScan_WithNullTicketType_ShouldHandleGracefully() {
        // Arrange - Create ticket without ticket type
        Ticket ticketWithoutType = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("NoType")
                .email("john@example.com")
                .event(mockEvent)
                .ticketType(null) // No ticket type
                .build();

        // Act & Assert
        assertDoesNotThrow(() -> ticketService.recordTicketScan(ticketWithoutType, mockZone));

        ArgumentCaptor<ScanEvent> scanEventCaptor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(1)).save(scanEventCaptor.capture());

        ScanEvent capturedScanEvent = scanEventCaptor.getValue();
        assertEquals(ticketWithoutType.getID(), capturedScanEvent.getId().getTicketId());
        assertEquals(mockZone, capturedScanEvent.getZone(), "Zone should still be set");
    }

    @Test
    void testRecordTicketScan_SpecialEventTickets_ShouldTrackPressAndStaff() throws InterruptedException {
        // Arrange - Create special ticket types
        TicketType pressType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Press")
                .zonePermissions(31L) // Full access - Binary: 11111 (zones 0,1,2,3,4)
                .build();

        TicketType staffType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Staff")
                .zonePermissions(15L) // Zones 0,1,2,3 - Binary: 1111
                .build();

        Ticket pressTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Reporter")
                .email("jane@newsnetwork.com")
                .event(mockEvent)
                .ticketType(pressType)
                .build();

        Ticket staffTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Mike")
                .lastName("Staff")
                .email("mike@eventcompany.com")
                .event(mockEvent)
                .ticketType(staffType)
                .build();

        // Act - Record multiple scans with slight delay to ensure unique timestamps
        ticketService.recordTicketScan(pressTicket, mockZone);
        Thread.sleep(1);
        ticketService.recordTicketScan(staffTicket, mockZone);

        // Assert
        verify(scanEventRepository, times(2)).save(any(ScanEvent.class));

        ArgumentCaptor<ScanEvent> scanEventCaptor = ArgumentCaptor.forClass(ScanEvent.class);
        verify(scanEventRepository, times(2)).save(scanEventCaptor.capture());

        var capturedScanEvents = scanEventCaptor.getAllValues();
        assertEquals(2, capturedScanEvents.size(), "Should have captured 2 scan events");

        // Verify timestamps are different (ensuring unique composite keys)
        LocalDateTime firstTimestamp = capturedScanEvents.get(0).getId().getTimestamp();
        LocalDateTime secondTimestamp = capturedScanEvents.get(1).getId().getTimestamp();

        assertNotEquals(firstTimestamp, secondTimestamp, "Timestamps should be different");

        // Verify ticket types are preserved in the scan data
        assertEquals(pressTicket.getID(), capturedScanEvents.get(0).getId().getTicketId());
        assertEquals(staffTicket.getID(), capturedScanEvents.get(1).getId().getTicketId());
    }

    // ...existing tests remain the same...
}
