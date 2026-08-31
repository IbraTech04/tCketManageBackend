-- SQLite mirror of the Postgres V4. See that file for why dismissal exists and why it
-- is a nullable timestamp rather than a boolean.
--
-- SQLite's ALTER TABLE adds one column per statement, and timestamptz becomes timestamp,
-- as in V1 and V3. The partial index syntax is the same on both engines.

ALTER TABLE "tcket:etransfer_receipts" ADD COLUMN dismissed_at   timestamp;
ALTER TABLE "tcket:etransfer_receipts" ADD COLUMN dismissed_by   varchar(255);
ALTER TABLE "tcket:etransfer_receipts" ADD COLUMN dismissal_note varchar(500);

CREATE INDEX idx_etransfer_receipt_unmatched
    ON "tcket:etransfer_receipts" (email_received_at DESC)
    WHERE order_id IS NULL AND dismissed_at IS NULL;
