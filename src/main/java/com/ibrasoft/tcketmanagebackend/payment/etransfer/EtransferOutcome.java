package com.ibrasoft.tcketmanagebackend.payment.etransfer;

/**
 * The result of feeding one received email through {@link EtransferConfirmationService}. Tells the
 * IMAP listener whether the message was acted on ({@link Status#CONFIRMED}) or could not be cleanly
 * matched and must be set aside for an operator ({@link Status#QUARANTINED}). The {@code detail} is
 * a human-readable reason for logging.
 */
public record EtransferOutcome(Status status, String detail) {

    public enum Status {
        /** An order was found, the amount checked out, and confirmation was applied (idempotently). */
        CONFIRMED,
        /** The email couldn't be trusted/matched/validated; route to the review folder. */
        QUARANTINED
    }

    public static EtransferOutcome confirmed(String detail) {
        return new EtransferOutcome(Status.CONFIRMED, detail);
    }

    public static EtransferOutcome quarantined(String detail) {
        return new EtransferOutcome(Status.QUARANTINED, detail);
    }

    public boolean isQuarantined() {
        return status == Status.QUARANTINED;
    }
}
