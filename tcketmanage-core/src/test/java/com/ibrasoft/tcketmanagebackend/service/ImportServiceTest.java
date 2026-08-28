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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the CSV attendee import's invariants:
 * <ul>
 *   <li>all-or-nothing — a single bad row persists nothing and reserves nothing;</li>
 *   <li>capacity is reserved in ticket-type UUID order, so a multi-type import cannot deadlock
 *       against a concurrent multi-type order (docs/LOCKING.MD rule 2);</li>
 *   <li>the import is bounded — a file over the configured row ceiling is rejected outright rather
 *       than accumulated into one unbounded transaction;</li>
 *   <li>a row whose email could never be delivered fails as a row error instead of being persisted
 *       as an undeliverable ticket.</li>
 * </ul>
 */
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

    /**
     * docs/LOCKING.MD rule 2: every multi-ticket-type path must take its ticket_types row locks in
     * ascending UUID order, or two such transactions can hold opposite ends of each other's lock
     * sets and deadlock. The per-type tallies are built in CSV order into a HashMap, whose iteration
     * order is hash-bucket order — neither CSV order nor UUID order — so the reserve loop has to
     * re-sort. The fixed UUIDs below are chosen so HashMap order differs from sorted order; this
     * test fails if the TreeMap in ImportService is ever "simplified" back to a plain forEach.
     */
    @Test
    void import_reservesTicketTypesInUuidOrder() {
        List<TicketType> types = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            types.add(TicketType.builder()
                    .id(UUID.fromString("0000000" + i + "-0000-0000-0000-00000000000" + i))
                    .event(event).name("T" + i).price(BigDecimal.TEN).build());
        }

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(types);
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Rows deliberately name the types in an order unrelated to their UUID order.
        MultipartFile file = csv("""
                first,last,email,type
                A,A,a@x.com,T3
                B,B,b@x.com,T1
                C,C,c@x.com,T5
                D,D,d@x.com,T2
                E,E,e@x.com,T4
                """);

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());
        assertEquals(5, result.getImported());

        ArgumentCaptor<UUID> reserved = ArgumentCaptor.forClass(UUID.class);
        verify(inventoryService, times(5)).reserve(reserved.capture(), anyInt());

        List<UUID> actual = reserved.getAllValues();
        List<UUID> expected = actual.stream().sorted().toList();
        assertEquals(expected, actual,
                "reserve() must be called in ascending ticket-type UUID order (docs/LOCKING.MD rule 2)");
    }

    /**
     * The importer accumulates every row into one list inside one transaction, so the row count is
     * capped. Boot's multipart limits bound bytes, not rows — a minimal 14-byte row means the 1 MB
     * default still admits ~75,000 of them.
     */
    @Test
    void import_overRowCeiling_rejectsWholeFile() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));

        importService.setMaxRows(3);
        MultipartFile file = csv("""
                first,last,email,type
                A,A,a@x.com,GA
                B,B,b@x.com,GA
                C,C,c@x.com,GA
                D,D,d@x.com,GA
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> importService.importAttendees(event.getId(), file, configWithTypeColumn()));
        assertTrue(ex.getMessage().contains("3"), () -> "message should name the limit: " + ex.getMessage());

        // Rejected outright, not partially applied.
        verify(ticketRepository, never()).saveAll(any());
        verify(inventoryService, never()).reserve(any(), anyInt());
    }

    /** A file exactly at the ceiling is still accepted — the cap is inclusive. */
    @Test
    void import_exactlyAtRowCeiling_succeeds() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        importService.setMaxRows(3);
        MultipartFile file = csv("""
                first,last,email,type
                A,A,a@x.com,GA
                B,B,b@x.com,GA
                C,C,c@x.com,GA
                """);

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(3, result.getImported());
        verify(inventoryService).reserve(ga.getId(), 3);
    }

    /**
     * A row whose address JavaMail cannot parse is reported as a row error (and, since the import is
     * all-or-nothing, sinks the file) rather than being persisted as a ticket that the later
     * asynchronous delivery job could only ever fail to send.
     */
    @Test
    void import_undeliverableEmail_reportedAsRowError() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));

        MultipartFile file = csv("first,last,email,type\nJane,Doe,jane[at]x.com,GA\n");

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(0, result.getImported());
        assertEquals(1, result.getErrors().size());
        assertEquals(2, result.getErrors().get(0).getRow());
        assertTrue(result.getErrors().get(0).getReason().toLowerCase().contains("email"));
        verify(ticketRepository, never()).saveAll(any());
        verify(inventoryService, never()).reserve(any(), anyInt());
    }

    /**
     * Over-long names are caught at the row level. Without this the value reaches Hibernate, whose
     * own {@code @Size(max = 50)} on Ticket throws at flush — a 500 with no row number, after the
     * inventory reservation has already been applied.
     */
    @Test
    void import_overlongName_reportedAsRowError() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEvent_Id(event.getId())).thenReturn(List.of(ga, vip));

        String tooLong = "x".repeat(51);
        MultipartFile file = csv("first,last,email,type\n" + tooLong + ",Doe,jane@x.com,GA\n");

        ImportResult result = importService.importAttendees(event.getId(), file, configWithTypeColumn());

        assertEquals(0, result.getImported());
        assertEquals(1, result.getErrors().size());
        verify(ticketRepository, never()).saveAll(any());
        verify(inventoryService, never()).reserve(any(), anyInt());
    }
}
