-- Backstop CHECK constraints on the capacity accounting in "tcket:ticket_types".
--
-- SECURITY: reserved_count is the only thing standing between the system and an oversell. It is
-- maintained exclusively by the atomic conditional UPDATEs in InventoryService
-- (`... SET reserved_count = reserved_count + :q WHERE id = :id AND (capacity IS NULL OR
-- reserved_count + :q <= capacity)`), which are correct as written — see docs/LOCKING.MD. These
-- constraints exist for the case where that is no longer true: a new code path that writes the
-- column directly, a botched compensating decrement in confirmPayment's late-payment branch, or a
-- future refactor that loses the `capacity IS NULL OR ...` guard. Without them the database will
-- happily persist reserved_count = -3 or reserved_count = capacity + 12 and the only symptom is
-- tickets sold for seats that do not exist, discovered at the door.
--
-- Rejected alternative: enforcing this solely in Java. Application-level checks are exactly what
-- the audit found to be missing (capacity was unreachable through POST /events/{id}/ticket-types,
-- so `capacity IS NULL` made the whole reserve guard vacuous for every type created that way).
-- A constraint in the schema cannot be bypassed by a new service, an import job, a psql session,
-- or a host application reaching into core's tables. It is the last line, not the first.
--
-- This migration FAILS LOUDLY if any existing row already violates it. That failure is a feature:
-- a pre-existing violation means the capacity accounting has already drifted in that deployment
-- and someone needs to look at it before the next sale, not after.
--
-- Know the blast radius before deploying this. Core runs its migrations from
-- TcketManageCoreMigrations.afterPropertiesSet(), a bean initializer on the *host's* DataSource,
-- so a violating row aborts the host application's startup entirely — LensBridge's own unrelated
-- endpoints included, not just ticketing. There is deliberately no property to skip this
-- constraint: a knob to boot anyway is a knob to keep overselling. To find out in advance, run
-- this against the target database before the deploy — it must return zero rows:
--
--     SELECT id, name, capacity, reserved_count
--       FROM "tcket:ticket_types"
--      WHERE reserved_count < 0
--         OR (capacity IS NOT NULL AND reserved_count > capacity);
--
-- Recovery is to reconcile reserved_count against the live orders that actually hold seats
-- (AWAITING_PAYMENT + PAID order_items per type, per InventoryService's accounting) and re-run —
-- or to raise the capacity to what was genuinely sold. Never to weaken the constraint.
--
-- Note: these are NOT added to the SQLite twin (db/tcketmanage/sqlite/V2__*.sql), which is a
-- deliberate no-op. See the comment in that file for why.

-- reserved_count is a count of seats consumed; a negative value can only mean a double-release.
-- InventoryService's release path already clamps at zero and logs loudly when it has to; this
-- catches the writer that does not.
ALTER TABLE "tcket:ticket_types"
    ADD CONSTRAINT ck_ticket_types_reserved_count_non_negative
    CHECK (reserved_count >= 0);

-- The oversell invariant itself. capacity IS NULL means unlimited (the entity documents this and
-- InventoryService's reserve guard is written the same way), so the NULL case must pass — a bare
-- `reserved_count <= capacity` would evaluate to NULL for unlimited types, which a CHECK treats as
-- satisfied anyway, but spelling it out keeps the constraint readable next to the query it mirrors.
--
-- Implies capacity >= 0 for any row with a non-NULL capacity, given the constraint above, so no
-- separate CHECK on capacity is needed. The API-level @Min(0) on the request DTOs rejects a
-- negative capacity before it ever reaches here.
ALTER TABLE "tcket:ticket_types"
    ADD CONSTRAINT ck_ticket_types_reserved_within_capacity
    CHECK (capacity IS NULL OR reserved_count <= capacity);
