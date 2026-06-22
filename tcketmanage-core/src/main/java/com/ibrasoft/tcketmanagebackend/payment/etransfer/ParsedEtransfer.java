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
 */
public record ParsedEtransfer(
        String message,
        String referenceCode,
        BigDecimal amount,
        String currency,
        String interacReferenceNumber
) {}
