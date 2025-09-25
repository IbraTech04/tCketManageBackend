package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CSVIndexMatte;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ScanEventRepository scanEventRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private TicketService ticketService;

    private Event testEvent;
    private CSVIndexMatte csvIndexMatte;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .time(LocalDateTime.now())
                .location("Test Location")
                .description("Test Description")
                .build();

        csvIndexMatte = new CSVIndexMatte();
        csvIndexMatte.setFirstNameIndex(0);
        csvIndexMatte.setLastNameIndex(1);
        csvIndexMatte.setEmailIndex(2);
    }

    @Test
    void testCreateTicket_Success() {
        // Given repository returns the saved ticket
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Ticket created = ticketService.createTicket("John", "Doe", "john.doe@example.com");

        // Then
        assertNotNull(created.getID());
        assertEquals("John", created.getFirstName());
        assertEquals("Doe", created.getLastName());
        assertEquals("john.doe@example.com", created.getEmail());
        verify(ticketRepository, times(1)).save(captor.capture());
        Ticket saved = captor.getValue();
        assertEquals("John", saved.getFirstName());
        assertEquals("Doe", saved.getLastName());
        assertEquals("john.doe@example.com", saved.getEmail());
    }

    @Test
    void testImportTicketsFromCSV_SavesEachRow() {
        // Given CSV as List<List<String>> aligned with CSVIndexMatte
        List<List<String>> csv = List.of(
                List.of("Alice", "Johnson", "alice@example.com"),
                List.of("Bob", "Smith", "bob@example.com"),
                List.of("Charlie", "Brown", "charlie@example.com")
        );

        // When
        ticketService.importTicketsFromCSV(csv, testEvent, csvIndexMatte);

        // Then
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(3)).save(captor.capture());
        List<Ticket> saved = captor.getAllValues();
        assertEquals(3, saved.size());
        assertEquals("Alice", saved.get(0).getFirstName());
        assertEquals("Johnson", saved.get(0).getLastName());
        assertEquals("alice@example.com", saved.get(0).getEmail());
        assertEquals(testEvent, saved.get(0).getEvent());
    }

    @Test
    void testFindTicketById_Found() {
        // Given
        UUID id = UUID.randomUUID();
        Ticket t = Ticket.builder()
                .ID(id)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .event(testEvent)
                .build();
        when(ticketRepository.findById(id)).thenReturn(Optional.of(t));

        // When
        Optional<Ticket> found = ticketService.findTicketById(id);

        // Then
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getID());
    }

    @Test
    void testFindTicketById_NotFound() {
        UUID id = UUID.randomUUID();
        when(ticketRepository.findById(id)).thenReturn(Optional.empty());
        Optional<Ticket> found = ticketService.findTicketById(id);
        assertTrue(found.isEmpty());
    }

    @Test
    void testGetZoneEntryCount_ReturnsCorrectCount() {
        // Given
        UUID ticketId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        
        Ticket ticket = Ticket.builder()
                .ID(ticketId)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();
                
        Zone zone = Zone.builder()
                .id(zoneId)
                .name("VIP Zone")
                .bitPosition(0)
                .build();
        
        int expectedCount = 3;
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId))
                .thenReturn(expectedCount);
        
        // When
        int actualCount = ticketService.getZoneEntryCount(ticket, zone);
        
        // Then
        assertEquals(expectedCount, actualCount);
        verify(scanEventRepository, times(1)).countZoneEntriesByTicketId(ticketId, zoneId);
    }

    @Test
    void testGetZoneEntryCount_ZeroEntries() {
        // Given
        UUID ticketId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        
        Ticket ticket = Ticket.builder()
                .ID(ticketId)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .build();
                
        Zone zone = Zone.builder()
                .id(zoneId)
                .name("General Zone")
                .bitPosition(1)
                .build();
        
        when(scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId))
                .thenReturn(0);
        
        // When
        int actualCount = ticketService.getZoneEntryCount(ticket, zone);
        
        // Then
        assertEquals(0, actualCount);
        verify(scanEventRepository, times(1)).countZoneEntriesByTicketId(ticketId, zoneId);
    }
}
