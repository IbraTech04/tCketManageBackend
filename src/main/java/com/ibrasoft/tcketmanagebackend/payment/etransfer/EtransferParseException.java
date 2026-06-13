package com.ibrasoft.tcketmanagebackend.payment.etransfer;

/**
 * Thrown when an email does not look like a parseable Interac e-Transfer notification (e.g. the
 * amount could not be located). Signals the listener to quarantine the message for manual review.
 */
public class EtransferParseException extends RuntimeException {

    public EtransferParseException(String message) {
        super(message);
    }
}
