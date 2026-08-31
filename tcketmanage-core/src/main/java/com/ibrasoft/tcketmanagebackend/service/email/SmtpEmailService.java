package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.properties.EmailProperties;
import com.ibrasoft.tcketmanagebackend.service.ticket.TicketGenerationService;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SMTP {@link EmailService} rendering two Thymeleaf templates: {@code ticketEmail}, one message per
 * ticket with the rendered QR ticket attached as a PNG, and {@code orderNotificationEmail}, one
 * message per order for the states that issue no tickets at all. Active only when
 * {@code tcketmanage.email.enabled=true}; otherwise {@link LoggingEmailService} handles delivery.
 *
 * <p>Delivery failures are logged but not rethrown, in both directions. Tickets are already
 * persisted by the time {@link com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService}
 * calls us, so a transient SMTP error must not roll back a paid, fulfilled order; and an order that
 * has expired or been cancelled has already released its inventory, so a bounced notice must not
 * undo that either.
 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(prefix = "tcketmanage.email", name = "enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final String TICKET_TEMPLATE_NAME = "tcketmanage/ticketEmail";
    private static final String ORDER_TEMPLATE_NAME = "tcketmanage/orderNotificationEmail";
    private static final String LOGO_PATH = "templates/tcketmanage/tCketManage.png";
    private static final int TICKET_WIDTH = 720;
    // 1:2 to match the template's 360x720 (18:9) viewBox, so the PNG isn't distorted.
    private static final int TICKET_HEIGHT = 1440;

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
            // MIXED_RELATED so the inline logo (multipart/related) and the ticket attachment
            // (multipart/mixed) both coexist. setText must precede addInline.
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(properties.getFrom(), properties.getFromName());
            helper.setTo(ticket.getEmail());
            helper.setSubject(subjectFor(ticket));
            helper.setText(body, true);
            // Embed the brand logo as a CID inline image referenced by <img src="cid:logo"> in the
            // template. SVG/data-URI logos are stripped by most mail clients, so this must be a PNG.
            helper.addInline("logo", new ClassPathResource(LOGO_PATH), "image/png");
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

    @Override
    public boolean sendOrderNotification(Order order, OrderNotification notification) {
        try {
            String body = renderBody(order, notification);

            MimeMessage message = mailSender.createMimeMessage();
            // RELATED is enough here — the inline logo is the only part; there's no attachment.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
            helper.setFrom(properties.getFrom(), properties.getFromName());
            helper.setTo(order.getBuyerEmail());
            helper.setSubject(notification.subject(eventName(order.getEvent())));
            helper.setText(body, true);
            helper.addInline("logo", new ClassPathResource(LOGO_PATH), "image/png");

            mailSender.send(message);
            log.info("Sent {} notification for order {} ({}) to {}",
                    notification, order.getId(), order.getReferenceCode(), order.getBuyerEmail());
            return true;
        } catch (Exception e) {
            // Non-fatal, and deliberately not retried: the order has already transitioned and
            // released its inventory: a bounced courtesy email must not disturb that.
            log.error("Failed to send {} notification for order {} ({}) to {}",
                    notification, order.getId(), order.getReferenceCode(), order.getBuyerEmail(), e);
            return false;
        }
    }

    private String renderBody(Order order, OrderNotification notification) {
        Event event = order.getEvent();
        OffsetDateTime time = event != null ? event.getTime() : null;

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("order", order);
        context.setVariable("notification", notification);
        context.setVariable("event", event);
        context.setVariable("eventDate", time != null ? time.format(DATE_FORMAT) : "");
        context.setVariable("eventTime", time != null ? time.format(TIME_FORMAT) : "");
        context.setVariable("amount", formatAmount(order));
        context.setVariable("seatCount", order.getItems() != null ? order.getItems().size() : 0);

        return templateEngine.process(ORDER_TEMPLATE_NAME, context);
    }

    /** e.g. {@code $75.00 CAD}. Currency-symbol-agnostic on purpose — the code carries the meaning. */
    private String formatAmount(Order order) {
        BigDecimal total = order.getAmountTotal() != null ? order.getAmountTotal() : BigDecimal.ZERO;
        return "$" + total.setScale(2, RoundingMode.HALF_UP) + " " + order.getCurrency();
    }

    private String renderBody(Ticket ticket) {
        Event event = ticket.getEvent();
        OffsetDateTime time = event != null ? event.getTime() : null;

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("ticket", ticket);
        context.setVariable("event", event);
        context.setVariable("eventDate", time != null ? time.format(DATE_FORMAT) : "");
        context.setVariable("eventTime", time != null ? time.format(TIME_FORMAT) : "");

        return templateEngine.process(TICKET_TEMPLATE_NAME, context);
    }

    private String subjectFor(Ticket ticket) {
        return "Your ticket for " + eventName(ticket.getEvent());
    }

    /** Subject-line-safe event name, falling back to a phrase that still reads as a sentence. */
    private String eventName(Event event) {
        return event != null && event.getName() != null ? event.getName() : "your event";
    }

    private String attachmentName(Ticket ticket) {
        return "ticket-" + ticket.getID() + ".png";
    }
}
