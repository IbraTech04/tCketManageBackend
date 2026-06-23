package com.ibrasoft.tcketmanagebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that exercise CryptoService and TicketGenerationService together
 * using real instances (no mocks, no Spring context).
 *
 * Split into two concerns:
 *  - QrPipeline: the data path Ticket → sign → verify, including tamper scenarios
 *  - Generation:  TicketGenerationService driving CryptoService via replaceTextFields
 */
class TicketQrIntegrationTest {

    private CryptoService cryptoService;
    private ObjectMapper objectMapper;
    private Ticket ticket;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();
        objectMapper = new ObjectMapper();
        cryptoService = new CryptoService(keyPair.getPrivate(), keyPair.getPublic(), objectMapper);

        Event event = Event.builder()
                .id(UUID.randomUUID())
                .name("Test Event")
                .location("Room 101")
                .description("Integration test event")
                .time(OffsetDateTime.of(2026, 9, 1, 18, 0, 0, 0, ZoneOffset.UTC))
                .build();

        ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .event(event)
                .ticketType(TicketType.builder().id(UUID.randomUUID()).name("General").build())
                .build();
    }

    // -------------------------------------------------------------------------
    // QR data pipeline: Ticket → fromTicket → sign → verify
    // -------------------------------------------------------------------------

    @Nested
    class QrPipeline {

        @Test
        void roundTrip_preservesAllFields() throws Exception {
            TicketQRData original = TicketQRData.fromTicket(ticket);

            TicketQRData decoded = cryptoService.verify(cryptoService.sign(original));

            assertEquals(ticket.getID(), decoded.getTicketID());
            assertEquals(ticket.getEvent().getId(), decoded.getEventID());
            assertEquals(original.getVersion(), decoded.getVersion());
        }

        @Test
        void roundTrip_isDeterministic() throws Exception {
            TicketQRData data = TicketQRData.fromTicket(ticket);

            // Ed25519 is deterministic — same ticket must always produce the same token
            String token1 = cryptoService.sign(data);
            String token2 = cryptoService.sign(data);

            assertEquals(token1, token2);
        }

        @Test
        void tamperedTicketId_inPayload_failsVerification() throws Exception {
            TicketQRData original = TicketQRData.fromTicket(ticket);
            String token = cryptoService.sign(original);
            String[] parts = token.split("\\.", 2);

            // Swap the ticketID inside the payload without re-signing
            TicketQRData forgedData = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), TicketQRData.class);
            forgedData.setTicketID(UUID.randomUUID());

            String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(forgedData));
            String forgedToken = forgedPayload + "." + parts[1];

            assertThrows(Exception.class, () -> cryptoService.verify(forgedToken),
                    "Swapping the payload without re-signing must be rejected");
        }

        @Test
        void tamperedSignature_failsVerification() throws Exception {
            String token = cryptoService.sign(TicketQRData.fromTicket(ticket));
            String tampered = token.substring(0, token.length() - 4) + "AAAA";

            assertThrows(Exception.class, () -> cryptoService.verify(tampered));
        }

        @Test
        void tokenSignedByAttackerKeyPair_failsVerification() throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            KeyPair attackerKeys = kpg.generateKeyPair();
            CryptoService attacker = new CryptoService(
                    attackerKeys.getPrivate(), attackerKeys.getPublic(), objectMapper);

            String forgedToken = attacker.sign(TicketQRData.fromTicket(ticket));

            assertThrows(Exception.class, () -> cryptoService.verify(forgedToken),
                    "Token signed by a different key pair must be rejected");
        }

        @Test
        void isValid_correctlyFiltersValidAndTamperedTokens() throws Exception {
            String valid = cryptoService.sign(TicketQRData.fromTicket(ticket));
            String tampered = valid.substring(0, valid.length() - 4) + "ZZZZ";

            assertTrue(cryptoService.isValid(valid));
            assertFalse(cryptoService.isValid(tampered));
            assertFalse(cryptoService.isValid("not-a-token"));
        }

        @Test
        void differentTickets_produceTokensThatDontCrossVerify() throws Exception {
            Ticket otherTicket = Ticket.builder()
                    .ID(UUID.randomUUID())
                    .event(ticket.getEvent())
                    .build();

            String token1 = cryptoService.sign(TicketQRData.fromTicket(ticket));
            String token2 = cryptoService.sign(TicketQRData.fromTicket(otherTicket));

            // Each token decodes to its own ticket, not the other's
            TicketQRData decoded1 = cryptoService.verify(token1);
            TicketQRData decoded2 = cryptoService.verify(token2);

            assertNotEquals(decoded1.getTicketID(), decoded2.getTicketID());
            assertEquals(ticket.getID(), decoded1.getTicketID());
            assertEquals(otherTicket.getID(), decoded2.getTicketID());
        }
    }

    // -------------------------------------------------------------------------
    // TicketGenerationService driving a real CryptoService
    // -------------------------------------------------------------------------

    @Nested
    class Generation {

        private TicketGenerationService generationService;

        @BeforeEach
        void setUp() {
            generationService = new TicketGenerationService(cryptoService, svgTemplateEngine());
        }

        @Test
        void renderTicketSvg_withRealCrypto_doesNotThrow() {
            assertDoesNotThrow(() -> generationService.renderTicketSvg(ticket));
        }

        @Test
        void renderTicketSvg_withRealCrypto_populatesQrGroup() throws Exception {
            String svg = generationService.renderTicketSvg(ticket);

            // The injected QR group should contain rect modules after generation
            assertTrue(svg.contains("<rect"), "QR group should contain rect elements after generation");
            assertTrue(svg.contains("fill=\"#17171c\""), "QR modules should use the configured fill");
        }

        @Test
        void renderTicketPng_withRealCrypto_producesPng() throws Exception {
            byte[] png = generationService.renderTicketPng(ticket, 720, 1440);

            assertNotNull(png);
            assertTrue(png.length > 0);
            assertEquals((byte) 0x89, png[0], "should be a PNG");
        }

        @Test
        void renderTicketSvg_differentTickets_produceDistinctSvgs() throws Exception {
            Ticket otherTicket = Ticket.builder()
                    .ID(UUID.randomUUID())
                    .firstName("Bob")
                    .lastName("Smith")
                    .email("bob@example.com")
                    .event(ticket.getEvent())
                    .ticketType(ticket.getTicketType())
                    .build();

            // SVG content differs because ticket IDs (and thus QR tokens) differ
            String svg1 = generationService.renderTicketSvg(ticket);
            String svg2 = generationService.renderTicketSvg(otherTicket);
            assertNotEquals(svg1, svg2);
        }

        private TemplateEngine svgTemplateEngine() {
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
}
