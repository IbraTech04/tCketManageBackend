package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.File;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketGenerationServiceTest {

    @Mock
    private CryptoService cryptoService;

    private TicketGenerationService ticketGenerationService;

    private Ticket mockTicket;
    private Event mockEvent;
    private TicketType mockTicketType;

    @BeforeEach
    void setUp() throws Exception {
        ticketGenerationService = new TicketGenerationService(cryptoService, svgTemplateEngine());

        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Sacred Commitments")
                .location("IB 120")
                .description("A Guide to not committing SSH Keys")
                .time(OffsetDateTime.of(2025, 10, 15, 14, 30, 0, 0, ZoneOffset.UTC))
                .build();

        mockTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General Admission")
                .build();

        mockTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Ibrahim")
                .lastName("Chehab")
                .email("ibrahim.chehab@mail.utoronto.ca")
                .event(mockEvent)
                .ticketType(mockTicketType)
                .build();

        lenient().when(cryptoService.sign(any(TicketQRData.class))).thenReturn("mocked-qr-token");
    }

    @Test
    void generateQRCodeSVG_producesPositionedModuleGroup() throws Exception {
        String qr = ticketGenerationService.generateQRCodeSVG(mockTicket);

        assertNotNull(qr);
        assertTrue(qr.startsWith("<g"), "QR fragment should be a <g> group");
        assertTrue(qr.contains("transform=\"translate("), "QR group should carry its own placement transform");
        assertTrue(qr.contains("<rect"));
        assertTrue(qr.contains("fill=\"#17171c\""));
        assertTrue(qr.endsWith("</g>"));

        verify(cryptoService, times(1)).sign(any(TicketQRData.class));
    }

    @Test
    void renderTicketSvg_fillsTemplateFieldsAndInjectsQr() throws Exception {
        String svg = ticketGenerationService.renderTicketSvg(mockTicket);

        assertNotNull(svg);
        assertTrue(svg.contains("Sacred Commitments"), "event name should be rendered");
        assertTrue(svg.contains("Ibrahim Chehab"), "ticket holder full name should be rendered");
        assertTrue(svg.contains("General Admission"), "ticket type should be rendered");
        assertTrue(svg.contains("IB 120"), "location should be rendered");
        // QR modules were injected via th:utext, not left as the template's sample markup
        assertTrue(svg.contains("fill=\"#17171c\""), "QR modules should be injected into the SVG");
        assertFalse(svg.contains("Lumen Summit 2026"), "sample placeholder text should be overwritten");

        verify(cryptoService, times(1)).sign(any(TicketQRData.class));
    }

    @Test
    void renderTicketPng_writesNonEmptyPngBytes() throws Exception {
        byte[] png = ticketGenerationService.renderTicketPng(mockTicket, 720, 1440);

        assertNotNull(png);
        assertTrue(png.length > 0, "PNG output should not be empty");
        // PNG magic number: 0x89 'P' 'N' 'G'
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);

        File outputPngFile = new File("target/test-output-ticket-general-admission.png");
        outputPngFile.getParentFile().mkdirs();
        Files.write(outputPngFile.toPath(), png);
        assertTrue(outputPngFile.exists() && outputPngFile.length() > 0);
    }

    @Test
    void renderTicketSvg_withNullTicketType_doesNotThrow() throws Exception {
        Ticket incompleteTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .event(mockEvent)
                .ticketType(null)
                .build();

        String svg = assertDoesNotThrow(() -> ticketGenerationService.renderTicketSvg(incompleteTicket));
        assertTrue(svg.contains("Jane Doe"));
    }

    @Test
    void renderTicketSvg_differentTickets_produceDistinctOutput() throws Exception {
        Ticket vipTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Sarah")
                .lastName("Wilson")
                .email("sarah.wilson@example.com")
                .event(mockEvent)
                .ticketType(TicketType.builder().id(UUID.randomUUID()).name("VIP").build())
                .build();

        String svgA = ticketGenerationService.renderTicketSvg(mockTicket);
        String svgB = ticketGenerationService.renderTicketSvg(vipTicket);

        assertNotEquals(svgA, svgB);
        assertTrue(svgB.contains("Sarah Wilson"));
        assertTrue(svgB.contains("VIP"));
    }

    /** Mirrors {@code SvgTemplateConfig} so the test exercises the same XML rendering path. */
    private static TemplateEngine svgTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".svg");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
