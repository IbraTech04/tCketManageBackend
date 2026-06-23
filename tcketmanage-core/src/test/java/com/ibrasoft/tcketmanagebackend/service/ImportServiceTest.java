package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.dto.request.ImportConfig;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ImportResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private ImportService importService;

    private Event event;
    private TicketType ga;
    private TicketType vip;

    @BeforeEach
    void setUp() {
        event = Event.builder().id(UUID.randomUUID()).name("Gala").time(OffsetDateTime.now())
                .location("Hall").description("D").build();
        ga = TicketType.builder().id(UUID.randomUUID()).event(event).name("GA").price(BigDecimal.TEN).build();
        vip = TicketType.builder().id(UUID.randomUUID()).event(event).name("VIP").price(BigDecimal.TEN).build();
    }

    private MultipartFile csv(String content) {
        return new MockMultipartFile("file", "attendees.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private ImportConfig configWithTypeColumn() {
        ImportConfig cfg = new ImportConfig();
        cfg.setFirstNameColumn(0);
        cfg.setLastNameColumn(1);
        cfg.setEmailColumn(2);
        cfg.setTicketTypeColumn(3);
        cfg.setHasHeaderRow(true);
        return cfg;
    }

    @Test
    void import_resolvesTypeByColumn_success() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile file = csv("first,last,email,type\nJane,Doe,jane@x.com,GA\nJohn,Roe,john@x.com,VIP\n");

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(2, result.getImported());
        assertTrue(result.getErrors().isEmpty());
        verify(ticketRepository, times(1)).saveAll(any());
        verify(inventoryService).reserve(ga.getId(), 1);
        verify(inventoryService).reserve(vip.getId(), 1);
    }

    @Test
    void import_fallsBackToDefaultType() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));
        when(ticketTypeRepository.findById(ga.getId())).thenReturn(Optional.of(ga));
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ImportConfig cfg = new ImportConfig();
        cfg.setFirstNameColumn(0);
        cfg.setLastNameColumn(1);
        cfg.setEmailColumn(2);
        cfg.setDefaultTicketTypeId(ga.getId());
        cfg.setHasHeaderRow(false);

        MultipartFile file = csv("Jane,Doe,jane@x.com\nJohn,Roe,john@x.com\n");

        ImportResult result = importService.importAttendees(event.getId(), file, cfg);

        assertEquals(2, result.getImported());
        verify(inventoryService).reserve(ga.getId(), 2);
    }

    @Test
    void import_unknownType_failsAllOrNothing() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));

        MultipartFile file = csv("first,last,email,type\nJane,Doe,jane@x.com,Bogus\n");

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(0, result.getImported());
        assertEquals(1, result.getErrors().size());
        assertEquals(2, result.getErrors().get(0).getRow()); // header is line 1, bad data row is line 2
        verify(ticketRepository, never()).saveAll(any());
        verify(inventoryService, never()).reserve(any(), eq(1));
    }

    @Test
    void import_missingField_reportedAsError() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));

        MultipartFile file = csv("first,last,email,type\nJane,Doe,,GA\n");

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(0, result.getImported());
        assertEquals(1, result.getErrors().size());
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void import_noTypeColumnAndNoDefault_throws() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        ImportConfig cfg = new ImportConfig();
        cfg.setFirstNameColumn(0);
        cfg.setLastNameColumn(1);
        cfg.setEmailColumn(2);

        MultipartFile file = csv("Jane,Doe,jane@x.com\n");

        assertThrows(IllegalArgumentException.class,
                () -> importService.importAttendees(event.getId(), file, cfg));
    }
}
