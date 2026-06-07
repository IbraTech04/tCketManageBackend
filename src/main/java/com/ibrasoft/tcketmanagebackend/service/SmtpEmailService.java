package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SMTP {@link EmailService} that renders the {@code ticketEmail} Thymeleaf template and sends one
 * email per ticket, with the rendered QR ticket attached as a PNG. Active only when
 * {@code app.email.enabled=true}; otherwise {@link LoggingEmailService} handles delivery.
 *
 * <p>Delivery failures are logged but not rethrown: tickets are already persisted by the time
 * {@link com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService} calls us, so a transient
 * SMTP error must not roll back a paid, fulfilled order.
 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final String TEMPLATE_NAME = "ticketEmail";
    private static final int TICKET_WIDTH = 720;
    private static final int TICKET_HEIGHT = 1280;

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final TicketGenerationService ticketGenerationService;
    private final EmailProperties properties;

    @Override
    public boolean sendTicket(Ticket ticket) {
        try {
            byte[] ticketPng = ticketGenerationService.renderTicketPng(ticket, TICKET_WIDTH, TICKET_HEIGHT);
            String body = renderBody(ticket);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, "UTF-8");
            helper.setFrom(properties.getFrom(), properties.getFromName());
            helper.setTo(ticket.getEmail());
            helper.setSubject(subjectFor(ticket));
            helper.setText(body, true);
            helper.addAttachment(attachmentName(ticket), new ByteArrayResource(ticketPng), "image/png");

            mailSender.send(message);
            log.info("Sent ticket {} to {}", ticket.getID(), ticket.getEmail());
            return true;
        } catch (Exception e) {
            // Non-fatal: the ticket is already issued; log and report failure so the caller leaves
            // lastTicketSent untouched (and a later "send missing"/resend can retry it).
            log.error("Failed to send ticket {} to {}", ticket.getID(), ticket.getEmail(), e);
            return false;
        }
    }

    private String renderBody(Ticket ticket) {
        Event event = ticket.getEvent();
        LocalDateTime time = event != null ? event.getTime() : null;

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("ticket", ticket);
        context.setVariable("event", event);
        context.setVariable("eventDate", time != null ? time.format(DATE_FORMAT) : "");
        context.setVariable("eventTime", time != null ? time.format(TIME_FORMAT) : "");

        return templateEngine.process(TEMPLATE_NAME, context);
    }

    private String subjectFor(Ticket ticket) {
        Event event = ticket.getEvent();
        String eventName = event != null && event.getName() != null ? event.getName() : "your event";
        return "Your ticket for " + eventName;
    }

    private String attachmentName(Ticket ticket) {
        return "ticket-" + ticket.getID() + ".png";
    }
}
