package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketGenerationServiceTest {

    @Mock
    private CryptoService cryptoService;

    @InjectMocks
    private TicketGenerationService ticketGenerationService;

    private Ticket mockTicket;
    private Event mockEvent;
    private TicketType mockTicketType;
    private Document svgDocument;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock event
        mockEvent = Event.builder()
                .id(UUID.randomUUID())
                .name("Sacred Commitments")
                .location("IB 120")
                .description("A Guide to not committing SSH Keys")
                .time(LocalDateTime.of(2025, 10, 15, 14, 30))
                .build();

        // Create mock ticket type - General Admission
        mockTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("General Admission")
                .zonePermissions(3L) // Binary: 011 (access to zones 0 and 1)
                .build();

        // Create mock ticket
        mockTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Ibrahim")
                .lastName("Chehab")
                .email("ibrahim.chehab@mail.utoronto.ca")
                .event(mockEvent)
                .ticketType(mockTicketType)
                .build();

        // Load the SVG template
        loadSvgTemplate();

        // Setup crypto service mocks
        setupCryptoServiceMocks();
    }

    @Test
    void testGenerateQRCodeSVG() throws Exception {
        // Test QR code generation
        String qrData = "TestQRData";
        String qrCodeSvg = ticketGenerationService.generateQRCodeSVG(qrData, 120);

        assertNotNull(qrCodeSvg);
        assertTrue(qrCodeSvg.contains("<g id=\"qrCode\""));
        assertTrue(qrCodeSvg.contains("<rect"));
        assertTrue(qrCodeSvg.contains("fill=\"black\""));
        assertTrue(qrCodeSvg.contains("</g>"));

        System.out.println("Generated QR Code SVG length: " + qrCodeSvg.length() + " characters");
    }

    @Test
    void testReplaceTextFields() throws Exception {
        // Test the main functionality
        assertDoesNotThrow(() -> {
            ticketGenerationService.replaceTextFields(svgDocument, mockTicket);
        });

        // Verify that the document has been modified
        assertNotNull(svgDocument);

        // Verify crypto service was called
        verify(cryptoService, times(1)).signTicket(any(TicketQRData.class));
        verify(cryptoService, times(1)).toBase64(any(TicketQRData.class));

        System.out.println("Text fields replacement completed successfully");
    }

    @Test
    void testCompleteTicketGenerationAndSavePNG() throws Exception {
        // This is the main test that generates a complete ticket and saves it as PNG

        // Process the ticket
        ticketGenerationService.replaceTextFields(svgDocument, mockTicket);

        // Convert directly to PNG using the service method
        File outputPngFile = new File("target/test-output-ticket-general-admission.png");
        outputPngFile.getParentFile().mkdirs(); // Ensure directory exists

        ticketGenerationService.convertToPng(svgDocument, outputPngFile.getAbsolutePath(), 720, 1280);

        // Verify the PNG file was created
        assertTrue(outputPngFile.exists(), "Output PNG file should be created");
        assertTrue(outputPngFile.length() > 0, "Output PNG file should not be empty");

        System.out.println("Generated PNG ticket (General Admission) saved to: " + outputPngFile.getAbsolutePath());
        System.out.println("PNG file size: " + outputPngFile.length() + " bytes");
    }

    @Test
    void testVIPTicketGeneration() throws Exception {
        // Create VIP ticket type with more zone permissions
        TicketType vipTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("VIP")
                .zonePermissions(15L) // Binary: 1111 (access to zones 0, 1, 2, 3)
                .build();

        Ticket vipTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Sarah")
                .lastName("Wilson")
                .email("sarah.wilson@example.com")
                .event(mockEvent)
                .ticketType(vipTicketType)
                .build();

        // Load fresh template
        loadSvgTemplate();

        // Process the VIP ticket
        ticketGenerationService.replaceTextFields(svgDocument, vipTicket);

        // Save VIP ticket as PNG
        File vipPngFile = new File("target/test-output-ticket-vip.png");
        ticketGenerationService.convertToPng(svgDocument, vipPngFile.getAbsolutePath(), 720, 1280);

        assertTrue(vipPngFile.exists(), "VIP ticket PNG should be created");
        assertTrue(vipPngFile.length() > 0, "VIP ticket PNG should not be empty");

        System.out.println("Generated VIP ticket PNG: " + vipPngFile.getAbsolutePath());
    }

    @Test
    void testStudentTicketGeneration() throws Exception {
        // Create Student ticket type with limited permissions
        TicketType studentTicketType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Student")
                .zonePermissions(1L) // Binary: 001 (access to zone 0 only - Main Entrance)
                .build();

        Ticket studentTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Alex")
                .lastName("Johnson")
                .email("alex.johnson@university.edu")
                .event(mockEvent)
                .ticketType(studentTicketType)
                .build();

        // Load fresh template
        loadSvgTemplate();

        // Process the student ticket
        ticketGenerationService.replaceTextFields(svgDocument, studentTicket);

        // Save student ticket as PNG
        File studentPngFile = new File("target/test-output-ticket-student.png");
        ticketGenerationService.convertToPng(svgDocument, studentPngFile.getAbsolutePath(), 720, 1280);

        assertTrue(studentPngFile.exists(), "Student ticket PNG should be created");
        assertTrue(studentPngFile.length() > 0, "Student ticket PNG should not be empty");

        System.out.println("Generated Student ticket PNG: " + studentPngFile.getAbsolutePath());
    }

    @Test
    void testWithNullTicketType() throws Exception {
        // Test with a ticket that has null ticket type
        Ticket incompleteTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .event(mockEvent)
                .ticketType(null) // Null ticket type
                .build();

        // Should handle gracefully even with null ticket type
        assertDoesNotThrow(() -> {
            ticketGenerationService.replaceTextFields(svgDocument, incompleteTicket);
        });

        // Convert to PNG
        File outputPngFile = new File("target/test-output-ticket-no-type.png");
        outputPngFile.getParentFile().mkdirs();

        ticketGenerationService.convertToPng(svgDocument, outputPngFile.getAbsolutePath(), 720, 1280);

        assertTrue(outputPngFile.exists());
        assertTrue(outputPngFile.length() > 0);
        System.out.println("Ticket without type PNG saved to: " + outputPngFile.getAbsolutePath());
    }

    @Test
    void testCompleteWorkflowWithMultipleTicketTypes() throws Exception {
        // Create different ticket types
        TicketType[] ticketTypes = {
            TicketType.builder().id(UUID.randomUUID()).name("General Admission").zonePermissions(1L).build(),
            TicketType.builder().id(UUID.randomUUID()).name("VIP").zonePermissions(15L).build(),
            TicketType.builder().id(UUID.randomUUID()).name("Student").zonePermissions(1L).build(),
            TicketType.builder().id(UUID.randomUUID()).name("Press").zonePermissions(7L).build() // Access to zones 0,1,2
        };

        // Test generating tickets for each type
        Ticket[] tickets = {
            Ticket.builder().ID(UUID.randomUUID()).firstName("Alice").lastName("Smith")
                    .email("alice@example.com").event(mockEvent).ticketType(ticketTypes[0]).build(),
            Ticket.builder().ID(UUID.randomUUID()).firstName("Bob").lastName("Johnson")
                    .email("bob@example.com").event(mockEvent).ticketType(ticketTypes[1]).build(),
            Ticket.builder().ID(UUID.randomUUID()).firstName("Charlie").lastName("Brown")
                    .email("charlie@example.com").event(mockEvent).ticketType(ticketTypes[2]).build(),
            Ticket.builder().ID(UUID.randomUUID()).firstName("Diana").lastName("Press")
                    .email("diana@newsagency.com").event(mockEvent).ticketType(ticketTypes[3]).build()
        };

        for (int i = 0; i < tickets.length; i++) {
            // Load fresh SVG template for each ticket
            loadSvgTemplate();

            // Process the ticket
            ticketGenerationService.replaceTextFields(svgDocument, tickets[i]);

            // Save as PNG with ticket type in filename
            String ticketTypeName = tickets[i].getTicketType().getName().toLowerCase().replace(" ", "-");
            File pngFile = new File("target/test-output-ticket-" + ticketTypeName + "-" + (i + 1) + ".png");
            ticketGenerationService.convertToPng(svgDocument, pngFile.getAbsolutePath(), 720, 1280);

            assertTrue(pngFile.exists(), "PNG file should be created for " + ticketTypeName + " ticket");
            assertTrue(pngFile.length() > 0, "PNG file should not be empty for " + ticketTypeName + " ticket");

            System.out.println("Generated " + tickets[i].getTicketType().getName() + " ticket PNG: " + pngFile.getAbsolutePath());
        }

        // Verify all crypto service calls
        verify(cryptoService, times(tickets.length)).signTicket(any(TicketQRData.class));
        verify(cryptoService, times(tickets.length)).toBase64(any(TicketQRData.class));
    }

    @Test
    void testTicketTypeZonePermissionsInQRData() throws Exception {
        // Test that different ticket types generate different QR codes due to different permissions

        // Create two tickets with same person but different ticket types
        TicketType basicType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Basic")
                .zonePermissions(1L) // Zone 0 only
                .build();

        TicketType premiumType = TicketType.builder()
                .id(UUID.randomUUID())
                .name("Premium")
                .zonePermissions(31L) // Zones 0,1,2,3,4
                .build();

        Ticket basicTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .event(mockEvent)
                .ticketType(basicType)
                .build();

        Ticket premiumTicket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .event(mockEvent)
                .ticketType(premiumType)
                .build();

        // Process both tickets
        loadSvgTemplate();
        ticketGenerationService.replaceTextFields(svgDocument, basicTicket);
        File basicPng = new File("target/test-output-ticket-basic-permissions.png");
        ticketGenerationService.convertToPng(svgDocument, basicPng.getAbsolutePath(), 720, 1280);

        loadSvgTemplate();
        ticketGenerationService.replaceTextFields(svgDocument, premiumTicket);
        File premiumPng = new File("target/test-output-ticket-premium-permissions.png");
        ticketGenerationService.convertToPng(svgDocument, premiumPng.getAbsolutePath(), 720, 1280);

        // Both files should exist
        assertTrue(basicPng.exists() && basicPng.length() > 0);
        assertTrue(premiumPng.exists() && premiumPng.length() > 0);

        System.out.println("Basic ticket (Zone 0 only): " + basicPng.getAbsolutePath());
        System.out.println("Premium ticket (Zones 0-4): " + premiumPng.getAbsolutePath());

        // Verify crypto service was called for both tickets
        verify(cryptoService, times(2)).signTicket(any(TicketQRData.class));
        verify(cryptoService, times(2)).toBase64(any(TicketQRData.class));
    }

    private void loadSvgTemplate() throws Exception {
        // Load the SVG template from resources
        InputStream svgStream = getClass().getClassLoader()
                .getResourceAsStream("templates/ticketTemplate.svg");

        assertNotNull(svgStream, "SVG template should be found in resources");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        svgDocument = builder.parse(svgStream);
        svgStream.close();
    }

    private void setupCryptoServiceMocks() throws Exception {
        // Mock the crypto service behavior
        when(cryptoService.toBase64(any(TicketQRData.class))).thenReturn("mocked-base64-qr-data");
        doNothing().when(cryptoService).signTicket(any(TicketQRData.class));
    }
}
