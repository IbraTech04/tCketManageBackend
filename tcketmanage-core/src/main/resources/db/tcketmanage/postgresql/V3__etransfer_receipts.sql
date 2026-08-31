-- Audit trail for inbound Interac e-Transfer notifications, mirroring
-- com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt.
--
-- One row per email the IMAP listener handled, whatever the verdict. order_id is nullable because an
-- email whose memo carried no recognizable code (or a code matching no order) still has to be
-- recorded -- those are exactly the ones an operator has to resolve. Everything except outcome,
-- email_received_at and created_at is nullable for the same reason: an email rejected at the
-- untrusted-sender or DMARC gate is recorded before it is ever parsed.
--
-- interac_reference is indexed but NOT unique: a redelivered notification is a harmless no-op at the
-- confirmation seam, and a unique constraint would turn that into a failure on an audit write.
--
-- email_received_at and created_at are timestamptz, per the note at the head of V1: Hibernate 6 maps
-- java.time.Instant to TIMESTAMP_UTC and a plain timestamp column fails ddl-auto=validate.

CREATE TABLE "tcket:etransfer_receipts" (
    id                  uuid          NOT NULL,
    order_id            uuid,
    outcome             varchar(20)   NOT NULL
                            CHECK (outcome IN ('CONFIRMED','QUARANTINED')),
    interac_reference   varchar(64),
    sender_name         varchar(255),
    sender_display_name varchar(255),
    sender_email        varchar(255),
    email_received_at   timestamptz   NOT NULL,
    body_date_text      varchar(64),
    amount              numeric(10,2),
    currency            varchar(3),
    memo                varchar(500),
    reference_code      varchar(32),
    detail              varchar(1000),
    created_at          timestamptz   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_etransfer_receipts_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id)
);

CREATE INDEX idx_etransfer_receipt_order ON "tcket:etransfer_receipts" (order_id);
CREATE INDEX idx_etransfer_receipt_interac_ref ON "tcket:etransfer_receipts" (interac_reference);
