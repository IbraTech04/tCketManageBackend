# tCketManage Concurrency Strategy

This document describes the concurrency and locking strategy used in tCketManage to handle the oversell problem inherent in ticket sales. The main challenge is to allow multiple buyers to attempt to purchase the same limited inventory of tickets without risking oversells or invalid states, while also maintaining high performance and responsiveness.

As stated previously, a large part of the architecture behind tCketManage was to allow for high throughput (in this case, high concurrency of buyers) without risking any payment-related race conditions. This was not much of a concern, if at all, when tCketManage was solely a ticket management system that took preexisting orders and processed them asynchronously. However, once the payment processing component was added, the risk of race conditions increased significantly, prompting the need for a well defined architecture to mitigate these risks.

At its core, the system stays correct by serializing every contended mutation on a **pessimistic write lock** (`SELECT ... FOR UPDATE`) over the single row that owns the invariant. In simple terms, the system uses two types of locks:

- Ticket-type row: The first lock any transaction meets, which guards capacity. Every `reserve` / `release` loads the `TicketType` via `findByIdForUpdate` (`PESSIMISTIC_WRITE`), so the check-then-increment of `reservedCount` against `capacity` is one atomic critical section. Two orders competing for the last seat are forced to take turns — the first commits `reserved = capacity`, the second re-reads that fresh value and is rejected with a `ConflictException`. Overselling is impossible because the "is there room?" test and the increment can never interleave.
- Order row: Once you've locked in your tickets, you now need to pay for them. This lock guards lifecycle transitions and ensures a consistent payment state throughout. Every state change (confirm, cancel, expire, refund) loads the order via `findByIdForUpdate`, so transitions to `PAID` / `CANCELLED` / `EXPIRED` serialize on that one row.

But why is this approach the _ideal_ approach? Well, let's compare it to the naive approach. Assume we were to use one big transaction around reserve + payment + persist rather than our previously defined granular locking system. This large lock would hold the ticket-type row lock for the entire duration of the payment provider's network call. That single lock is shared by *every* buyer of that ticket type, so one slow Stripe round-trip would block all concurrent purchases of the same ticket and could exhaust the DB connection pool under load. At best, this would lead to a poor user experience; at worst, it could cause the system to grind to a halt.

`OrderService.createOrder` is therefore deliberately **not** `@Transactional`. It splits the work into three independent phases routed through a separate `OrderTransactions` bean (a separate bean so Spring's proxy actually applies the `@Transactional` boundaries; a self-invocation would bypass the proxy and silently run in one scope):

1. `reserveAndPersist` (committed): lock the ticket-type rows, reserve all seats all-or-nothing, persist the order as `AWAITING_PAYMENT`. The commit **releases the inventory locks** before any external call.
2. `provider.initiate(...)` (no transaction, no lock held): contact the payment provider. Slowness here blocks nobody, and others can continue to reserve the same ticket type (up to capacity) while this runs. The provider returns a reference code and whether it settled synchronously.
3. `finalizeInitiation` (committed): take a fresh lock on the *order* row to record the provider ref and, if the provider settled synchronously, confirm payment.

The seats are safely held between phases by the committed `reservedCount`, not by a held lock. If phase 2 fails, `releaseHold` rolls the reservation back so seats aren't stranded until the sweep.

## How the order-row lock makes transitions safe

Three actors can touch an `AWAITING_PAYMENT` order concurrently: an incoming payment confirmation, a buyer cancel, and the scheduled expiry sweep. Because each re-loads the order under the row lock **and re-checks status inside the lock**, they serialize cleanly:

- **Idempotent confirmation:** A redelivered webhook or a double-click finds the order already `PAID` and returns a no-op instead of double-fulfilling. (At-least-once delivery is assumed, so this matters.)
- **No double release:** the expiry sweep does an unlocked candidate query, then re-loads each candidate `findByIdForUpdate` and re-checks it is still `AWAITING_PAYMENT`. If a buyer-cancel or late confirmation moved it first, the sweep skips it, so a hold is never released twice.
- **Late payments:** An Interac e-transfer can land after the hold expired or was cancelled. `confirmPayment` handles `EXPIRED` / `CANCELLED` by trying to re-acquire the seats (`tryReserveAll`, which locks and checks every row before incrementing any, so no partial reservation can be left behind). If the seats are gone, the order goes to `REFUND_PENDING` rather than overselling.

The net invariant: **`sum(reserved seats) <= capacity` always holds, and every order reaches exactly one terminal state**, regardless of how confirmations, cancels, and sweeps interleave.

> *Note for the dev database: SQLite by default serializes everything, so the above strategy doesn't really apply locally. The real gains will be seen when using a proper DBMS like MySQL or PostgreSQL*