package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import jakarta.mail.BodyPart;
import jakarta.mail.Folder;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/**
 * Bridges the IMAP IDLE adapter to {@link EtransferConfirmationService}. For each received message
 * it extracts the {@code From} address and HTML body, runs the confirmation policy, and - when the
 * email is quarantined - copies it into the review folder for an operator. Already-processed mail is
 * kept out of future fetches by the receiver's mark-as-read (see {@code EtransferImapConfig}).
 *
 * <p>The receiver is configured with {@code autoCloseFolder=false} so the live {@code jakarta.mail}
 * message (and its open folder) is available here; we close that folder via the closeable-resource
 * header when done.
 */
class EtransferMailHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EtransferMailHandler.class);

    private final EtransferConfirmationService confirmationService;
    private final String reviewFolder;

    EtransferMailHandler(EtransferConfirmationService confirmationService, String reviewFolder) {
        this.confirmationService = confirmationService;
        this.reviewFolder = reviewFolder;
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        if (!(message.getPayload() instanceof jakarta.mail.Message mail)) {
            return;
        }
        try {
            String from = extractFrom(mail);
            String[] authResults = mail.getHeader("Authentication-Results");
            String body = extractBody(mail);
            if (body == null) {
                log.warn("Received e-Transfer email with no text/html body from {}; quarantining.", from);
                moveToReview(mail);
                return;
            }

            EtransferOutcome outcome = confirmationService.process(new ReceivedEmail(
                    from, extractDisplayName(mail), extractReceivedAt(mail), authResults, body));
            if (outcome.isQuarantined()) {
                moveToReview(mail);
            }
        } catch (Exception e) {
            // Never let a single bad email kill the listener; flag it and move on.
            log.error("Failed to handle incoming e-Transfer email; attempting to quarantine.", e);
            safeMoveToReview(mail);
        } finally {
            closeResource(message);
        }
    }

    /** Returns the bare {@code From} address (no display name), or {@code null} if absent. */
    private static String extractFrom(jakarta.mail.Message mail) throws jakarta.mail.MessagingException {
        jakarta.mail.Address[] from = mail.getFrom();
        if (from == null || from.length == 0) {
            return null;
        }
        if (from[0] instanceof InternetAddress internet) {
            return internet.getAddress();
        }
        return from[0].toString();
    }

    /** Returns the {@code From} display name, or {@code null} when the header carried none. */
    private static String extractDisplayName(jakarta.mail.Message mail) throws jakarta.mail.MessagingException {
        jakarta.mail.Address[] from = mail.getFrom();
        if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress internet)) {
            return null;
        }
        String personal = internet.getPersonal();
        return (personal == null || personal.isBlank()) ? null : personal.trim();
    }

    /**
     * When the mail server took delivery. Prefers the server's own {@code INTERNALDATE} over the
     * sender-supplied {@code Date} header, since the latter is set by the sending side. Falls back to
     * now if the store reports neither, so a receipt always has a usable timestamp - the small loss of
     * fidelity beats leaving the audit record blank.
     */
    private static Instant extractReceivedAt(jakarta.mail.Message mail) {
        try {
            Date received = mail.getReceivedDate();
            if (received != null) {
                return received.toInstant();
            }
            Date sent = mail.getSentDate();
            if (sent != null) {
                return sent.toInstant();
            }
        } catch (jakarta.mail.MessagingException e) {
            log.debug("Could not read date headers from e-Transfer email; falling back to now.", e);
        }
        return Instant.now();
    }

    /** Depth-first search for the HTML part, falling back to any text part. */
    private static String extractBody(Part part) throws jakarta.mail.MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String textFallback = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                if (child.isMimeType("text/html")) {
                    String html = extractBody(child);
                    if (html != null) {
                        return html; // prefer HTML
                    }
                } else if (child.isMimeType("text/plain") && textFallback == null) {
                    textFallback = (String) child.getContent();
                } else if (child.isMimeType("multipart/*")) {
                    String nested = extractBody(child);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
            return textFallback;
        }
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        return null;
    }

    private void safeMoveToReview(jakarta.mail.Message mail) {
        try {
            moveToReview(mail);
        } catch (Exception e) {
            log.error("Could not quarantine e-Transfer email to review folder '{}'.", reviewFolder, e);
        }
    }

    /**
     * Copies the message into the review folder (creating it on first use). We copy rather than move:
     * the original stays in the inbox flagged read so the listener won't re-process it, while the
     * review folder gives an operator a clean queue of everything that needs a human decision.
     */
    private void moveToReview(jakarta.mail.Message mail) throws jakarta.mail.MessagingException {
        Folder inbox = mail.getFolder();
        Store store = inbox.getStore();
        Folder review = store.getFolder(reviewFolder);
        if (!review.exists()) {
            review.create(Folder.HOLDS_MESSAGES);
            review.setSubscribed(true);
        }
        inbox.copyMessages(new jakarta.mail.Message[]{mail}, review);
    }

    private static void closeResource(Message<?> message) {
        Closeable resource = message.getHeaders()
                .get(IntegrationMessageHeaderAccessor.CLOSEABLE_RESOURCE, Closeable.class);
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                log.debug("Error closing IMAP folder resource.", e);
            }
        }
    }
}
