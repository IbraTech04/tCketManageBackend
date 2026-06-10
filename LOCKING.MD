# tCketManage Concurrency Strategy

This document describes the concurrency and locking strategy used in tCketManage to handle the oversell problem inherent in ticket sales. The main challenge is to allow multiple buyers to attempt to purchase the same limited inventory of tickets without risking oversells or invalid states, while also maintaining high performance and responsiveness.

As stated previously, a large part of the architecture behind tCketManage was to allow for high throughput (in this case, high concurrency of buyers) without risking any payment-related race conditions. This was not much of a concern, if at all, when tCketManage was solely a ticket management system that took preexisting orders and processed them asynchronously. However, once the payment processing component was added, the risk of race conditions increased significantly, prompting the need for a well defined architecture to mitigate these risks.

At its core, the system stays correct by serializing every contended mutation on the single row that owns the invariant. The two invariants are guarded by two different mechanisms, each chosen for the failure mode it has to defeat:

- Ticket-type row (guards capacity): every `reserve` / `release` is an **atomic conditional UPDATE** — `UPDATE ticket_types SET reserved_count = reserved_count + :q WHERE id = :id AND (capacity IS NULL OR reserved_count + :q <= capacity)` — and the caller checks the affected-row count. The "is there room?" test and the increment execute inside one SQL statement under the row's write lock, so they can never interleave: two orders competing for the last seat take turns at the database, the first one wins, the second matches no row and is rejected with a `ConflictException`. Releases are the mirror image (`WHERE reserved_count >= :q`, with an atomic clamp-at-zero fallback that logs loudly, since underflow means a double-release bug). We deliberately do **not** implement this as "lock the row, check in Java, save": a JPA locking query that resolves to an entity already in the persistence context (e.g. a `TicketType` loaded eagerly through an order's items) acquires the DB lock but returns the *stale* managed instance, silently turning "check under lock" into "check a pre-lock value" — exactly the oversell the lock was meant to prevent. The atomic statement is immune to persistence-context staleness and holds the row lock for a single statement instead of a whole load-check-flush cycle.
- Order row (guards lifecycle transitions): a **pessimistic write lock** (`SELECT ... FOR UPDATE` via `findByIdForUpdate`). Every state change (confirm, cancel, expire, refund) re-loads the order under this lock and re-checks status inside it, so transitions to `PAID` / `CANCELLED` / `EXPIRED` serialize on that one row. (The stale-instance pitfall above doesn't bite here because the locked load is always the first read of that order row in its transaction.)

But why is this approach the _preferable_ approach? Well, let's compare it to the naive approach. Assume we were to use one big transaction around reserve + payment + persist rather than our previously defined granular locking system. This large lock would hold the ticket-type row lock for the entire duration of the payment provider's network call. That single lock is shared by *every* buyer of that ticket type, so one slow Stripe round-trip would block all concurrent purchases of the same ticket and could exhaust the DB connection pool under load. At best, this would lead to a poor user experience; at worst, it could cause the system to grind to a halt.

`OrderService.createOrder` is therefore deliberately **not** `@Transactional`. It splits the work into three independent phases routed through a separate `OrderTransactions` bean (a separate bean so Spring's proxy actually applies the `@Transactional` boundaries; a self-invocation would bypass the proxy and silently run in one scope):

1. `reserveAndPersist` (committed): lock the ticket-type rows, reserve all seats all-or-nothing, persist the order as `AWAITING_PAYMENT`. The commit **releases the inventory locks** before any external call.
2. `provider.initiate(...)` (no transaction, no lock held): contact the payment provider. Slowness here blocks nobody, and others can continue to reserve the same ticket type (up to capacity) while this runs. The provider returns a reference code and whether it settled synchronously.
3. `finalizeInitiation` (committed): take a fresh lock on the *order* row to record the provider ref and, if the provider settled synchronously, confirm payment.

The seats are safely held between phases by the committed `reservedCount`, not by a held lock. If phase 2 fails, `releaseHold` rolls the reservation back so seats aren't stranded until the sweep.

## How the order-row lock makes transitions safe

Three actors can touch an `AWAITING_PAYMENT` order concurrently: an incoming payment confirmation, a buyer cancel, and the scheduled expiry sweep. Because each re-loads the order under the row lock **and re-checks status inside the lock**, they serialize cleanly:

- **Idempotent confirmation:** A redelivered webhook or a double-click finds the order already `PAID` and returns a no-op instead of double-fulfilling. (At-least-once delivery is assumed, so this matters.)
- **No double release:** the expiry sweep does an unlocked candidate query, then expires each candidate **in its own transaction** that re-loads it `findByIdForUpdate` and re-checks it is still `AWAITING_PAYMENT`. If a buyer-cancel or late confirmation moved it first, the sweep skips it, so a hold is never released twice. One transaction per candidate is load-bearing: a single sweep-wide transaction would accumulate order-row and ticket-type locks across candidates in an order that violates the global acquisition order below (deadlocking against concurrent confirms/cancels), and one failed candidate would roll back every other expiry.
- **Late payments:** An Interac e-transfer can land after the hold expired or was cancelled. `confirmPayment` handles `EXPIRED` / `CANCELLED` by trying to re-acquire the seats (`tryReserveAll`, which applies the atomic conditional reserve per type in UUID order and, if one comes up short, undoes the earlier increments with explicit compensating decrements — not a rollback, because the surrounding confirm transaction still has to commit the order's move to `REFUND_PENDING`). If the seats are gone, the order goes to `REFUND_PENDING` rather than overselling.

The net invariant: **`sum(reserved seats) <= capacity` always holds, and every order reaches exactly one terminal state**, regardless of how confirmations, cancels, and sweeps interleave.

> *Note for the dev database: SQLite by default serializes everything, so the above strategy doesn't really apply locally. The real gains will be seen when using a proper DBMS like MySQL or PostgreSQL*

## Avoiding deadlocks

Simply put, we sort all locks in a global order and acquire them in that order. If it's enough for the dining philosophers, it's enough for us :). Concretely:

1. **Order row before ticket-type rows.** Every transaction that touches both (confirm, cancel, expire, release-hold) locks the order row first, then mutates ticket-type rows. No path acquires an order-row lock while already holding a ticket-type lock — this is why the expiry sweep must not batch candidates into one transaction.
2. **Ticket-type rows in UUID order.** Both reservation (`reserveAndPersist`, `tryReserveAll`) and release (`releaseAll`) sort by ticket-type UUID before applying their updates, so two multi-type transactions can never hold opposite ends of each other's lock sets.

## Edits that race the counter

`reserved_count` is owned by the atomic statements in `InventoryService`; nothing else may write it. Admin edits to a ticket type take the same row lock (`findByIdForUpdate`) before flushing, and the entity is mapped `@DynamicUpdate` so an unrelated edit only writes the columns it changed — a full-row UPDATE would otherwise overwrite a concurrent reservation with the stale `reserved_count` it read at load time.