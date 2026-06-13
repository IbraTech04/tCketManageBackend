package com.ibrasoft.tcketmanagebackend.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deployment-level payment configuration, bound from {@code payments.*}.
 */
@Component
@ConfigurationProperties(prefix = "payments")
@Data
public class PaymentProperties {

    /** Provider id used when an order request doesn't specify one. */
    private String defaultProvider = "mock";

    /**
     * Shared secret required (as the {@code X-Admin-Token} header) to call operator endpoints such
     * as manual payment confirmation. Interim measure until real authentication is added.
     */
    private String adminToken;

    private Mock mock = new Mock();
    private Interac interac = new Interac();

    @Data
    public static class Mock {
        private boolean enabled = true;
        /** When true, {@code initiate} auto-confirms (Completed); when false, returns Instructions. */
        private boolean autoConfirm = true;
        private long holdMinutes = 30;
    }

    @Data
    public static class Interac {
        private boolean enabled = false;
        private String payeeEmail;
        private long holdHours = 48;

        /** Inbound IMAP listener that auto-confirms orders from received e-Transfer emails. */
        private Imap imap = new Imap();
    }

    /**
     * IMAP inbound config for the e-Transfer auto-confirmation listener, bound from
     * {@code payments.interac.imap.*}. Gated independently of {@link Interac#enabled}: a deployment
     * can offer the manual reference-code flow without (or before) wiring the mailbox listener.
     */
    @Data
    public static class Imap {

        /** Master switch for the IMAP IDLE listener. */
        private boolean enabled = false;

        private String host;
        private int port = 993;
        private String username;
        private String password;

        /** Mailbox folder to watch for incoming notifications. */
        private String folder = "INBOX";

        /**
         * {@code From} addresses whose mail is trusted as genuine Interac notifications. Mail from
         * any other sender is quarantined rather than acted on.
         */
        private List<String> expectedSenders = List.of("notify@payments.interac.ca");

        /**
         * Folder a message is moved to when it can't be cleanly matched and confirmed (unknown memo
         * code, amount mismatch, untrusted sender, parse failure). An operator reviews it and, if
         * legitimate, settles the order via the manual-confirm endpoint.
         */
        private String reviewFolder = "NeedsReview";

        /**
         * When true (default), the e-Transfer amount must equal the order total exactly or the
         * message is quarantined. Defence-in-depth on top of the unguessable memo code.
         */
        private boolean requireExactAmount = true;
    }
}
