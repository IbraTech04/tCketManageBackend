package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end delivery check: builds a random event + ticket, renders the QR
 * ticket to PNG, renders
 * the HTML email body, and sends the ticket via the real SMTP
 * {@link SmtpEmailService}.
 *
 * <p>
 * This sends a <b>real email</b>, so it is gated behind the
 * {@code RUN_EMAIL_IT} environment
 * variable to keep it out of normal builds/CI. Run it explicitly with:
 * 
 * <pre>
 *   $env:RUN_EMAIL_IT="true"; ./mvnw -o test -Dtest=TicketEmailDeliveryIT
 * </pre>
 * 
 * It also requires {@code app.email.enabled=true} plus valid
 * {@code spring.mail.*} credentials in
 * {@code application.properties} (otherwise the SMTP sender isn't wired / can't
 * connect).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EMAIL_IT", matches = "true")
class TicketEmailDeliveryIT {

        @Value("${app.test.recipient:}")
        private String RECIPIENT;

        @Autowired
        private EmailService emailService;

        @Autowired
        private TicketGenerationService ticketGenerationService;

        @Test
        void generatesAndEmailsRandomTicket() {
                // 1. Random event
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                Event event = Event.builder()
                                .id(UUID.randomUUID())
                                .name("Test Event " + suffix)
                                .description("Automated end-to-end ticket delivery.")
                                .location("IB 120, University of Toronto Mississauga")
                                .time(LocalDateTime.now().plusDays(14).withHour(19).withMinute(30).withSecond(0)
                                                .withNano(0))
                                .build();

                // 2. Random ticket addressed to the recipient
                TicketType ticketType = TicketType.builder()
                                .id(UUID.randomUUID())
                                .name("General Admission")
                                .price(new BigDecimal("25.00"))
                                .event(event)
                                .build();

                Ticket ticket = Ticket.builder()
                                .ID(UUID.randomUUID())
                                .firstName("Ibrahim")
                                .lastName("Chehab")
                                .email(RECIPIENT)
                                .event(event)
                                .ticketType(ticketType)
                                .status(TicketStatus.ACTIVE)
                                .build();

                // 3. Generate the QR ticket PNG (intermediate sanity check)
                byte[] ticketPng = ticketGenerationService.renderTicketPng(ticket, 720, 1440);
                assertNotNull(ticketPng, "Ticket PNG should be rendered");
                assertTrue(ticketPng.length > 0, "Ticket PNG should not be empty");

                // 4 + 5. Render the HTML email and send it with the ticket attached
                assertTrue(emailService.sendTicket(ticket), "Ticket email should send successfully");

                System.out.printf("Sent ticket %s (%d byte PNG) to %s%n", ticket.getID(), ticketPng.length, RECIPIENT);
        }
}
