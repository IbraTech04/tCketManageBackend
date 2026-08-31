package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An e-Transfer that arrived but was never tied to an order, awaiting an operator's decision.
 *
 * <p>Everything the notification carried is exposed, because the whole job here is letting a human
 * recognise a payment the software could not: the memo is what they will read to spot the mistyped
 * code, and the payer name and amount are what they will check against a bank statement.
 */
@Data
@Builder
@AllArgsConstructor
public class UnmatchedPaymentResponse {

    private UUID id;
    /** The buyer-typed memo, verbatim - usually where the mistyped reference code is. */
    private String memo;
    /** The XXXX-XXXX code the parser recovered, when it found a well-formed one that matched no order. */
    private String referenceCode;
    private String interacReference;
    private String senderName;
    private String senderEmail;
    private BigDecimal amount;
    private String currency;
    /** When the mail server took delivery - the authoritative arrival time. */
    private Instant emailReceivedAt;
    /** The notification's own Date line, verbatim, for reconciling against a bank statement. */
    private String bodyDateText;
    /** Why the pipeline set this aside. */
    private String detail;

    public static UnmatchedPaymentResponse from(EtransferReceipt receipt) {
        return UnmatchedPaymentResponse.builder()
                .id(receipt.getId())
                .memo(receipt.getMemo())
                .referenceCode(receipt.getReferenceCode())
                .interacReference(receipt.getInteracReference())
                .senderName(receipt.getSenderName())
                .senderEmail(receipt.getSenderEmail())
                .amount(receipt.getAmount())
                .currency(receipt.getCurrency())
                .emailReceivedAt(receipt.getEmailReceivedAt())
                .bodyDateText(receipt.getBodyDateText())
                .detail(receipt.getDetail())
                .build();
    }
}
