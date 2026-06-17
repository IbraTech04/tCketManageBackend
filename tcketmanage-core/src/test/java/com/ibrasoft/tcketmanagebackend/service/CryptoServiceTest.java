package com.ibrasoft.tcketmanagebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CryptoServiceTest {

    private CryptoService cryptoService;
    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        testKeyPair = kpg.generateKeyPair();
        cryptoService = new CryptoService(testKeyPair.getPrivate(), testKeyPair.getPublic(), new ObjectMapper());
    }

    private TicketQRData ticket(UUID ticketId, UUID eventId) {
        return TicketQRData.builder().ticketID(ticketId).eventID(eventId).build();
    }

    // --- sign ---

    @Test
    void sign_validData_returnsTokenWithTwoParts() throws Exception {
        UUID id = UUID.randomUUID();
        String token = cryptoService.sign(ticket(id, id));

        assertNotNull(token);
        assertEquals(2, token.split("\\.", -1).length, "Token must have exactly one '.'");
    }

    @Test
    void sign_sameDataTwice_returnsSameToken() throws Exception {
        UUID id = UUID.randomUUID();
        String t1 = cryptoService.sign(ticket(id, id));
        String t2 = cryptoService.sign(ticket(id, id));

        assertEquals(t1, t2, "Ed25519 is deterministic — same input must yield same token");
    }

    @Test
    void sign_differentData_returnsDifferentTokens() throws Exception {
        String t1 = cryptoService.sign(ticket(UUID.randomUUID(), UUID.randomUUID()));
        String t2 = cryptoService.sign(ticket(UUID.randomUUID(), UUID.randomUUID()));

        assertNotEquals(t1, t2);
    }

    @Test
    void sign_nullTicketId_throws() {
        TicketQRData bad = TicketQRData.builder().ticketID(null).eventID(UUID.randomUUID()).build();
        assertThrows(Exception.class, () -> cryptoService.sign(bad));
    }

    @Test
    void sign_nullEventId_throws() {
        TicketQRData bad = TicketQRData.builder().ticketID(UUID.randomUUID()).eventID(null).build();
        assertThrows(Exception.class, () -> cryptoService.sign(bad));
    }

    // --- verify ---

    @Test
    void verify_validToken_returnsCorrectData() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TicketQRData original = ticket(ticketId, eventId);

        TicketQRData decoded = cryptoService.verify(cryptoService.sign(original));

        assertEquals(ticketId, decoded.getTicketID());
        assertEquals(eventId, decoded.getEventID());
        assertEquals(original.getVersion(), decoded.getVersion());
    }

    @Test
    void verify_tamperedPayload_throws() throws Exception {
        UUID id = UUID.randomUUID();
        String token = cryptoService.sign(ticket(id, id));
        String[] parts = token.split("\\.", 2);
        // Replace payload with a different ticket's payload
        String otherPayload = cryptoService.sign(ticket(UUID.randomUUID(), UUID.randomUUID())).split("\\.", 2)[0];
        String tampered = otherPayload + "." + parts[1];

        assertThrows(Exception.class, () -> cryptoService.verify(tampered));
    }

    @Test
    void verify_tamperedSignature_throws() throws Exception {
        UUID id = UUID.randomUUID();
        String token = cryptoService.sign(ticket(id, id));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(Exception.class, () -> cryptoService.verify(tampered));
    }

    @Test
    void verify_missingDot_throws() {
        assertThrows(Exception.class, () -> cryptoService.verify("nodothere"));
    }

    @Test
    void verify_wrongKeyPair_throws() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair other = kpg.generateKeyPair();
        CryptoService otherService = new CryptoService(other.getPrivate(), other.getPublic(), new ObjectMapper());

        UUID id = UUID.randomUUID();
        String tokenSignedByOther = otherService.sign(ticket(id, id));

        assertThrows(Exception.class, () -> cryptoService.verify(tokenSignedByOther));
    }

    // --- isValid ---

    @Test
    void isValid_validToken_returnsTrue() throws Exception {
        UUID id = UUID.randomUUID();
        assertTrue(cryptoService.isValid(cryptoService.sign(ticket(id, id))));
    }

    @Test
    void isValid_tamperedToken_returnsFalse() throws Exception {
        UUID id = UUID.randomUUID();
        String token = cryptoService.sign(ticket(id, id));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertFalse(cryptoService.isValid(tampered));
    }

    @Test
    void isValid_malformedToken_returnsFalse() {
        assertFalse(cryptoService.isValid("garbage"));
    }
}
