package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import java.time.Instant;

/**
 * One inbound notification as the IMAP listener saw it, before any interpretation. Carries the
 * envelope facts that live in the MIME headers rather than the HTML body, so
 * {@link EtransferConfirmationService} can record them on the receipt even for an email it rejects
 * before parsing.
 *
 * <p>Two of these fields deliberately duplicate information the body also carries, because the two
 * sources can disagree and the disagreement is itself worth seeing:
 * <ul>
 *   <li>{@code fromDisplayName} is whatever the sending client rendered; the body's
 *       {@code Sent From:} is the payer's name as their bank holds it. The body wins for display,
 *       this is the fallback.</li>
 *   <li>{@code receivedAt} is a real, zoned instant from the mail server. The body's {@code Date:}
 *       line is date-only and localized, so it is kept as text only. This is the timestamp anything
 *       machine-readable should use.</li>
 * </ul>
 *
 * @param fromAddress           bare {@code From} address, no display name; {@code null} if absent
 * @param fromDisplayName       {@code From} personal name, or {@code null} if the header carried none
 * @param receivedAt            when the mail server took delivery; never {@code null}
 * @param authenticationResults every {@code Authentication-Results} header value in message order,
 *                              or {@code null}; consulted only when DMARC enforcement is enabled
 * @param html                  the email's HTML body (or its text/plain fallback)
 */
public record ReceivedEmail(
        String fromAddress,
        String fromDisplayName,
        Instant receivedAt,
        String[] authenticationResults,
        String html
) {}
