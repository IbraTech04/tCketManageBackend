package com.ibrasoft.tcketmanagebackend.model.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An audit record of one inbound Interac e-Transfer notification and what we decided about it.
 *
 * <p>Written for <em>every</em> email the listener handles, not just the ones that settle an order.
 * Before this existed, a payment that couldn't be matched left no trace in the database at all: the
 * order flipped to {@code QUARANTINED}, the message was copied to an IMAP folder, and the facts an
 * operator needs to resolve it - who sent it, how much, which Interac reference, when it landed -
 * survived only in a log line. A receipt row is what lets a review queue explain itself without
 * anyone opening the mailbox.
 *
 * <p>{@link #order} is nullable on purpose. An email whose memo carried no recognizable code, or a
 * code matching no order, still produces a receipt - those are precisely the ones a human has to
 * look at. For the same reason nearly every field is nullable: an email rejected at the
 * untrusted-sender or DMARC gate is recorded before it is ever parsed, so all that is known about it
 * is the envelope.
 *
 * <p>{@link #interacReference} is indexed but <strong>not</strong> unique. A redelivered notification
 * is currently a harmless no-op at the confirmation seam, and a unique constraint would convert that
 * into a constraint violation on an audit write - exactly the wrong place to start failing.
 */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "tcket:etransfer_receipts",
       indexes = {
           @Index(name = "idx_etransfer_receipt_order", columnList = "order_id"),
           @Index(name = "idx_etransfer_receipt_interac_ref", columnList = "interac_reference")
       })
public class EtransferReceipt {

    /** What the confirmation policy decided about the email this receipt records. */
    public enum Outcome {
        /** Matched an order, passed every check, and was settled. */
        CONFIRMED,
        /** Failed a check; the email was set aside for an operator. */
        QUARANTINED
    }

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /**
     * The order this payment settled or was set aside against, or {@code null} when the email could
     * not be tied to one. Lazy and excluded from {@code toString}/{@code equals} so an unrendered
     * proxy on a detached receipt can't trigger a load.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    /**
     * The same {@code order_id}, mapped read-only as a plain scalar. Lets a caller group receipts by
     * order without touching {@link #order}, whose lazy proxy would otherwise be initialized - one
     * extra select per row - purely to read an id the row already contains. Written via
     * {@link #order}; this mapping is insert/update-disabled so the column has exactly one owner.
     */
    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    /** Interac's own reference number for the transfer, as printed in the notification. */
    @Column(name = "interac_reference", length = 64)
    private String interacReference;

    /** The payer's name from the body's {@code Sent From:} line - the bank's version of it. */
    @Column(length = 255)
    private String senderName;

    /** The {@code From} header's display name; kept alongside {@link #senderName} so a mismatch shows. */
    @Column(length = 255)
    private String senderDisplayName;

    /** The bare {@code From} address the notification arrived from. */
    @Column(length = 255)
    private String senderEmail;

    /** When the mail server took delivery. The authoritative timestamp for this email. */
    @Column(nullable = false)
    private Instant emailReceivedAt;

    /**
     * The body's {@code Date:} line verbatim (e.g. {@code "June 6, 2026"}). Date-only, unzoned and
     * localized, so it is stored as text for operator reconciliation and never parsed. Use
     * {@link #emailReceivedAt} for anything computational.
     */
    @Column(length = 64)
    private String bodyDateText;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    /** The buyer-typed memo, verbatim. */
    @Column(length = 500)
    private String memo;

    /** The {@code XXXX-XXXX} code recovered from the memo, even when it matched no order. */
    @Column(length = 32)
    private String referenceCode;

    /** Why it was quarantined, or a summary of the confirmation. */
    @Column(length = 1000)
    private String detail;

    /** When we processed the email (distinct from {@link #emailReceivedAt}, when it arrived). */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When an operator wrote this payment off as never going to match an order, or {@code null} while
     * it is still open. Together with a null {@link #order} this is what defines the review queue:
     * unmatched and not yet dismissed.
     *
     * <p>Linking a receipt to an order removes it from the queue by filling {@link #order} in, so
     * dismissal exists only for the payments that genuinely match nothing — a transfer sent to the
     * wrong organisation, or a duplicate that is not owed tickets. Without an exit those rows would
     * sit at the top of the queue forever and operators would learn to ignore it.
     */
    private Instant dismissedAt;

    /** Who dismissed it, for the audit trail. {@code null} unless {@link #dismissedAt} is set. */
    @Column(length = 255)
    private String dismissedBy;

    /** Optional operator note explaining the dismissal. */
    @Column(length = 500)
    private String dismissalNote;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
