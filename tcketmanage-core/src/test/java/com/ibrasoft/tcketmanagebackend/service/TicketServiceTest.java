package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
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
}
