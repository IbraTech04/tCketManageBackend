package com.ibrasoft.tcketmanagebackend.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Deployment-level payment configuration, bound from {@code tcketmanage.payments.*}.
 *
 * <p>Every time-valued property here is a {@link Duration}, so it is written with an explicit unit
 * suffix — {@code 30s}, {@code 90m}, {@code 48h}, {@code 2d}. A bare number is read as milliseconds.
 * Each provider states its hold window under the same {@code <provider>.hold} key rather than in a
 * unit baked into the property name, so changing "48 hours" to "90 minutes" is a value edit and the
 * providers stay comparable at a glance.
 */
@Component
@ConfigurationProperties(prefix = "tcketmanage.payments")
@Data
public class PaymentProperties {

    /** Provider id used when an order request doesn't specify one. */
    private String defaultProvider = "mock";

    /**
     * How often unpaid orders past their hold window are expired and their reserved inventory
     * released. Run by core's own scheduler; see
     * {@link com.ibrasoft.tcketmanagebackend.service.order.OrderExpiryScheduler}.
     *
     * <p>This is the sweep cadence, not the deadline: an order actually expires somewhere in
     * {@code [hold, hold + sweep-interval]}, so keep it well under the shortest provider hold.
     */
    private Duration sweepInterval = Duration.ofSeconds(60);

    private Mock mock = new Mock();
    private Stripe stripe = new Stripe();
    private Interac interac = new Interac();

    @Data
    public static class Mock {
        private boolean enabled = true;
        /** When true, {@code initiate} auto-confirms (Completed); when false, returns Instructions. */
        private boolean autoConfirm = true;
        /** How long an unpaid mock order holds its seats. */
        private Duration hold = Duration.ofMinutes(30);
    }

    /**
     * Stripe Checkout, bound from {@code tcketmanage.payments.stripe.*}.
     *
     * <p>Note that {@code stripe.enabled} is read only by the {@code @ConditionalOnProperty} on
     * {@link com.ibrasoft.tcketmanagebackend.payment.provider.StripePaymentProvider} and has no field
     * here — bean gating happens before this class is bound, so the flag has to be read from the
     * environment directly.
     */
    @Data
    public static class Stripe {
        /** How long an unpaid Stripe order holds its seats while the Checkout session is open. */
        private Duration hold = Duration.ofMinutes(30);
    }

    @Data
    public static class Interac {
        private boolean enabled = false;
        private String payeeEmail;
        /**
         * How long an unpaid e-Transfer order holds its seats. Generous by default because
         * e-Transfers settle slowly — the buyer may need to clear their bank's send limits.
         */
        private Duration hold = Duration.ofHours(48);

        /** Inbound IMAP listener that auto-confirms orders from received e-Transfer emails. */
        private Imap imap = new Imap();
    }

    /**
     * IMAP inbound config for the e-Transfer auto-confirmation listener, bound from
     * {@code tcketmanage.payments.interac.imap.*}. Gated independently of {@link Interac#enabled}: a deployment
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

        /** Optional DMARC enforcement based on the mail server's {@code Authentication-Results}. */
        private Dmarc dmarc = new Dmarc();
    }

    /**
     * Opt-in DMARC enforcement, bound from {@code tcketmanage.payments.interac.imap.dmarc.*}. DMARC is evaluated
     * by the receiving mail server, which records the verdict in an {@code Authentication-Results}
     * header; we read that header rather than re-validating SPF/DKIM ourselves.
     *
     * <p>Disabled by default so a deployment whose mailbox provider doesn't stamp the header (or
     * hasn't configured this) keeps the From-match-only behaviour instead of quarantining every
     * payment. When enabled, a message that isn't an aligned {@code dmarc=pass} is quarantined
     * (fail-closed), consistent with the rest of the pipeline.
     */
    @Data
    public static class Dmarc {

        /** Master switch for DMARC enforcement. */
        private boolean enabled = false;

        /**
         * The {@code authserv-id} your own receiving mail server writes as the first token of the
         * {@code Authentication-Results} header it adds (e.g. {@code mail.lensbridge.tech}). Only the
         * header bearing this id is trusted; any other (a spoofer can forge {@code Authentication-Results}
         * lines in the message body) is ignored. Required when {@link #enabled}.
         */
        private String authservId;

        /**
         * Domain the authenticated {@code header.from} must align with. Optional: when blank, defaults
         * to the domain of the trusted {@code From} address of the message being checked, so the DMARC
         * verdict is confirmed to apply to the sender we already trust.
         */
        private String alignedDomain;
    }
}
