-- SQLite mirror of the Postgres V3. See that file for why order_id and most columns are nullable and
-- why interac_reference is indexed rather than unique.
--
-- Dialect differences from the Postgres copy, matching how V1 already splits: uuid -> blob,
-- timestamptz -> timestamp, numeric(10,2) -> numeric.

CREATE TABLE "tcket:etransfer_receipts" (
    id                  blob         NOT NULL,
    order_id            blob,
    outcome             varchar(20)  NOT NULL
                            CHECK (outcome IN ('CONFIRMED','QUARANTINED')),
    interac_reference   varchar(64),
    sender_name         varchar(255),
    sender_display_name varchar(255),
    sender_email        varchar(255),
    email_received_at   timestamp    NOT NULL,
    body_date_text      varchar(64),
    amount              numeric,
    currency            varchar(3),
    memo                varchar(500),
    reference_code      varchar(32),
    detail              varchar(1000),
    created_at          timestamp    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_etransfer_receipts_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id)
);

CREATE INDEX idx_etransfer_receipt_order ON "tcket:etransfer_receipts" (order_id);
CREATE INDEX idx_etransfer_receipt_interac_ref ON "tcket:etransfer_receipts" (interac_reference);
