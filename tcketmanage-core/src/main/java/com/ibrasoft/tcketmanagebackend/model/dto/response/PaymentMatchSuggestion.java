package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One order an unmatched payment might belong to, with the evidence that put it in the list.
 *
 * <p>The reasons are returned rather than folded into an opaque score on purpose. An operator is
 * being asked to attach real money to an order, and "amount matches and the code is one character
 * out" is a claim they can check; "score 0.87" is one they can only defer to. Deferring to a number
 * is how the wrong order gets confirmed.
 */
@Data
@Builder
@AllArgsConstructor
public class PaymentMatchSuggestion {

    private UUID orderId;
    private String referenceCode;
    private String buyerEmail;
    private String status;
    private BigDecimal amountTotal;
    private String currency;
    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Single-character edits between the memo and this order's code. {@code 0} means the code is
     * present verbatim and something else (an amount mismatch) is why it was never matched.
     */
    private int codeDistance;

    /** Whether the received amount equals this order's total, currency included. */
    private boolean amountMatches;

    /** Whether the payment landed between the order being placed and its hold lapsing. */
    private boolean withinHoldWindow;
}
