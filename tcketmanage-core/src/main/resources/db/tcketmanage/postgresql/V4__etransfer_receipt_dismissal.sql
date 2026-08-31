-- Lets an unmatched e-Transfer be taken out of the review queue without being linked
-- to an order.
--
-- The queue is "receipts belonging to no order": order_id IS NULL. Linking one fills
-- order_id in, so it leaves the queue on its own. What has no exit today is a payment
-- that will never match anything -- someone e-transferred the wrong organisation, or
-- sent twice and the duplicate is not owed tickets. Without this those rows sit at the
-- top of the queue forever and operators learn to ignore the queue.
--
-- Kept as a nullable timestamp rather than a boolean so the queue predicate stays a
-- plain IS NULL on both columns, and so "when was this written off, and by whom" is
-- answerable later.

ALTER TABLE "tcket:etransfer_receipts"
    ADD COLUMN dismissed_at   timestamptz,
    ADD COLUMN dismissed_by   varchar(255),
    ADD COLUMN dismissal_note varchar(500);

-- Partial index matching the queue query exactly: the open rows are a small minority
-- of the table and the settled ones never need scanning.
CREATE INDEX idx_etransfer_receipt_unmatched
    ON "tcket:etransfer_receipts" (email_received_at DESC)
    WHERE order_id IS NULL AND dismissed_at IS NULL;
