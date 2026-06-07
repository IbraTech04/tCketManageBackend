package com.ibrasoft.tcketmanagebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.*;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoServiceTest {

        private CryptoService cryptoService;

        @Mock
        private ObjectMapper mockObjectMapper;

        private PrivateKey testPrivateKey;
        private PublicKey testPublicKey;
        private KeyPair testKeyPair;

        @BeforeEach
        void setUp() throws Exception {

                KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");

                testKeyPair = kpg.generateKeyPair();
                testPrivateKey = testKeyPair.getPrivate();
                testPublicKey = testKeyPair.getPublic();

                cryptoService = new CryptoService(
                                testPrivateKey,
                                testPublicKey,
                                new ObjectMapper());
        }

        @Test
        void testSignTicket_ValidTicket_ShouldGenerateSignature() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();

                // Act
                cryptoService.signTicket(ticket);

                // Assert
                assertNotNull(ticket.getSignature(), "Signature should not be null after signing");
                assertFalse(ticket.getSignature().isEmpty(), "Signature should not be empty");

                // Verify it's a valid Base64 string
                assertDoesNotThrow(() -> Base64.getDecoder().decode(ticket.getSignature()),
                                "Signature should be valid Base64");
        }

        @Test
        void testSignTicket_SameTicketSignedTwice_ShouldGenerateSameSignature() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();
                TicketQRData ticket1 = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();
                TicketQRData ticket2 = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();

                // Act
                cryptoService.signTicket(ticket1);
                cryptoService.signTicket(ticket2);

                // Assert
                assertEquals(ticket1.getSignature(), ticket2.getSignature(),
                                "Same ticket ID should generate identical signatures");
        }

        @Test
        void testSignTicket_DifferentTickets_ShouldGenerateDifferentSignatures() throws Exception {
                // Arrange
                TicketQRData ticket1 = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .eventID(UUID.randomUUID())
                                .build();
                TicketQRData ticket2 = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .eventID(UUID.randomUUID())
                                .build();

                // Act
                cryptoService.signTicket(ticket1);
                cryptoService.signTicket(ticket2);

                // Assert
                assertNotEquals(ticket1.getSignature(), ticket2.getSignature(),
                                "Different ticket IDs should generate different signatures");
        }

        @Test
        void testValidateSignature_ValidSignature_ShouldReturnTrue() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();

                // Sign the ticket first
                cryptoService.signTicket(ticket);

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertTrue(isValid, "Valid signature should be validated as true");
        }

        @Test
        void testValidateSignature_TamperedTicketId_ShouldReturnFalse() throws Exception {
                // Arrange
                UUID originalTicketId = UUID.randomUUID();
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(originalTicketId)
                                .eventID(originalTicketId)
                                .build();

                // Sign the ticket first
                cryptoService.signTicket(ticket);

                // Tamper with the ticket ID
                ticket.setTicketID(UUID.randomUUID());

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Tampered ticket ID should make signature invalid");
        }

        @Test
        void testValidateSignature_TamperedSignature_ShouldReturnFalse() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();

                // Sign the ticket first
                cryptoService.signTicket(ticket);

                // Tamper with the signature
                String originalSignature = ticket.getSignature();
                String tamperedSignature = originalSignature.substring(0, originalSignature.length() - 5) + "AAAAA";
                ticket.setSignature(tamperedSignature);

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Tampered signature should be invalid");
        }

        @Test
        void testValidateSignature_NullSignature_ShouldReturnFalse() {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .signature(null)
                                .build();

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Null signature should be invalid");
        }

        @Test
        void testValidateSignature_EmptySignature_ShouldReturnFalse() {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .signature("")
                                .build();

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Empty signature should be invalid");
        }

        @Test
        void testValidateSignature_InvalidBase64Signature_ShouldReturnFalse() {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .signature("invalid-base64!@#$%")
                                .build();

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Invalid Base64 signature should be invalid");
        }

        @Test
        void testValidateSignature_DifferentKeyPair_ShouldReturnFalse() throws Exception {

                // Arrange 
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");

                KeyPair keyPair1 = kpg.generateKeyPair();
                CryptoService cryptoService1 = new CryptoService(
                                keyPair1.getPrivate(),
                                keyPair1.getPublic(),
                                new ObjectMapper());

                KeyPair keyPair2 = kpg.generateKeyPair();
                CryptoService cryptoService2 = new CryptoService(
                                keyPair2.getPrivate(),
                                keyPair2.getPublic(),
                                new ObjectMapper());

                UUID testId = UUID.randomUUID();

                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(testId)
                                .eventID(testId)
                                .build();

                // Sign with second keypair
                cryptoService2.signTicket(ticket);

                // Act 
                boolean isValid = cryptoService1.validateSignature(ticket);

                // Assert
                assertFalse(isValid,
                                "Signature from different key pair should be invalid");
        }

        @Test
        void testSignTicket_NullTicketId_ShouldHandleGracefully() throws Exception {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(null)
                                .build();

                // Act & Assert
                assertThrows(Exception.class, () -> cryptoService.signTicket(ticket),
                                "Signing ticket with null ID should throw exception");
        }

        @Test
        void testValidateSignature_NullTicketId_ShouldReturnFalse() throws Exception {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(null)
                                .signature("some-signature")
                                .build();

                // Act
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertFalse(isValid, "Ticket with null ID should have invalid signature");
        }

        @Test
        void testToBase64_ValidObject_ShouldReturnBase64String() throws Exception {
                // Arrange
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .signature("test-signature")
                                .build();

                // Act
                String base64Result = cryptoService.toBase64(ticket);

                // Assert
                assertNotNull(base64Result, "Base64 result should not be null");
                assertFalse(base64Result.isEmpty(), "Base64 result should not be empty");

                // Verify it's valid Base64
                assertDoesNotThrow(() -> Base64.getDecoder().decode(base64Result),
                                "Result should be valid Base64");
        }

        @Test
        void testToBase64_WithMockedObjectMapper_ShouldHandleException() throws Exception {
                // Arrange
                CryptoService cryptoServiceWithMock = new CryptoService(testPrivateKey, testPublicKey,
                                mockObjectMapper);
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(UUID.randomUUID())
                                .eventID(UUID.randomUUID())
                                .build();

                when(mockObjectMapper.writeValueAsBytes(any()))
                                .thenThrow(new RuntimeException("JSON processing error"));

                // Act & Assert
                assertThrows(Exception.class, () -> cryptoServiceWithMock.toBase64(ticket),
                                "Should propagate ObjectMapper exceptions");
        }

        @Test
        void testSignAndValidate_RoundTrip_ShouldWork() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();
                TicketQRData ticket = TicketQRData.builder()
                                .ticketID(testTicketId)
                                .eventID(testTicketId)
                                .build();

                // Act
                cryptoService.signTicket(ticket);
                boolean isValid = cryptoService.validateSignature(ticket);

                // Assert
                assertTrue(isValid, "Round-trip sign and validate should work");
                assertNotNull(ticket.getSignature(), "Ticket should have signature after signing");
        }

        @Test
        void testSignatureConsistency_MultipleSigns_ShouldBeDeterministic() throws Exception {
                // Arrange
                UUID testTicketId = UUID.randomUUID();

                // Act - Sign the same ticket multiple times
                String[] signatures = new String[5];
                for (int i = 0; i < 5; i++) {
                        TicketQRData ticket = TicketQRData.builder()
                                        .ticketID(testTicketId)
                                        .eventID(testTicketId)
                                        .build();
                        cryptoService.signTicket(ticket);
                        signatures[i] = ticket.getSignature();
                }

                // Assert - All signatures should be identical
                for (int i = 1; i < signatures.length; i++) {
                        assertEquals(signatures[0], signatures[i],
                                        "Signature " + i + " should match the first signature - signing should be deterministic");
                }
        }
}
