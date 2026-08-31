package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import java.math.BigDecimal;

/**
 * The fields extracted from an Interac e-Transfer notification email body. Provider-neutral data
 * carrier produced by {@link InteracEmailParser} and consumed by
 * {@link EtransferConfirmationService}.
 *
 * @param message               the buyer-typed memo, verbatim (may contain extra words around the code)
 * @param referenceCode         the order reference code recovered from {@code message} in canonical
 *                              {@code XXXX-XXXX} form, or {@code null} if no code pattern was present
 * @param amount                the received amount
 * @param currency              the ISO currency code (defaults to {@code CAD} when absent)
 * @param interacReferenceNumber Interac's own reference number for the transfer (audit/dedup), or
 *                              {@code null} if absent
 * @param sentFrom              the payer's name as Interac reports it ({@code Sent From:}), or
 *                              {@code null} if absent
 * @param bodyDateText          the {@code Date:} line <em>verbatim</em>, or {@code null} if absent.
 *                              Deliberately not parsed into a temporal type: it is date-only, carries
 *                              no time or zone, and is localized (Interac sends an FR variant), so it
 *                              is kept as an operator-facing string for reconciliation only. The
 *                              machine-readable timestamp is {@link ReceivedEmail#receivedAt()}.
 */
public record ParsedEtransfer(
        String message,
        String referenceCode,
        BigDecimal amount,
        String currency,
        String interacReferenceNumber,
        String sentFrom,
        String bodyDateText
) {}
