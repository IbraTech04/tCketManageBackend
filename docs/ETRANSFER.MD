# tCketManage Backend - Interac E-Transfer Payment Flow

Many small school clubs rely on Interac E-Transfer for ticket sales, but unlike a system like Stripe, E-Transfers are not designed to integrate with third-party applications. There are no official APIs (and anything unofficial is legally dubious at best), which means event organizers have to manually confirm and monitor for incoming payments, and then manually mark orders as paid in the system. This is the main issue tCketManage aims to solve with its E-Transfer payment provider. 

## Payment Flow

At its core, every tCketManage order has a unique confirmation code in the form `XXXX-XXXX` that the buyer includes in the message of their E-Transfer. Using IMAP, tCketManage monitors the inbox of the email address configured in `payments.interac.payee-email` for incoming E-Transfer notifications. When a new email arrives, the system:

1. Confirms the sender is a valid Interac notification
2. Parses the email to extract the confirmation code
3. Matches it to an existing order, and if a match is found
4. Marks the order as paid and sends a confirmation email to the buyer
5. If the email cannot be parsed or matched, it is moved to a quarantine folder for manual review by the event organizer. In the future this will also be flagged by the frontend admin dashboard

## Receipts

Every notification the listener handles writes a row to `tcket:etransfer_receipts`, whether it settled an order or was set aside. The row records what the email said — Interac's own reference number, the payer's name from the `Sent From:` line, the amount, the memo, and when the mail server took delivery — plus the verdict and, for a rejection, the reason.

This exists so the review queue can explain itself. Before it, a payment that couldn't be matched left nothing in the database: the order flipped to `QUARANTINED`, the message was copied to an IMAP folder, and the facts an organizer needs to resolve it survived only in a log line. `order_id` is nullable precisely so an e-Transfer that matched *no* order — the case most needing a human — is still recorded.

Two details worth knowing when reading these rows:

- **`email_received_at` is the timestamp to trust.** It comes from the MIME headers. The body's `Date:` line is kept verbatim in `body_date_text` for reconciliation against a bank statement, but it is date-only, unzoned, and localized (Interac sends a French variant), so it is never parsed.
- **`paid_at` on the order and `email_received_at` on the receipt are different things.** The first is when tCketManage settled the order; the second is when the notification arrived. They usually differ by seconds, and by much more if the listener was down.

The receipt write deliberately runs in its own transaction, separate from the payment confirmation — see `EtransferReceiptRecorder` for why, and why a failure to write one never undoes a payment that was already confirmed.

## The unmatched-payment queue

There are two ways an e-Transfer fails to settle, and they are not equally visible.

If the memo carries a valid code but something else is wrong — usually the amount — the order is found, so it moves to `QUARANTINED`, appears in the dashboard's review tab, and keeps holding its seats until someone approves or denies it.

If the memo carries no usable code, **no order is found at all**. Nothing moves, nothing is flagged, and the buyer's order sits in `AWAITING_PAYMENT` until the expiry sweep releases their seats and the tickets go back on sale. They paid. Until this queue existed the only trace was an IMAP folder nobody opens.

`GET /payments/unmatched` lists those payments — receipts with no order that haven't been written off. Opening one asks for `/suggestions`, which ranks the orders it might belong to.

**Ranking leans on the reference code, because that is the only signal that reflects what the buyer actually typed.** The common failure is a mistyped code, not a missing one, and the code alphabet omits I, L, O and U precisely because those are the characters people transcribe wrong — so a botched code is usually one or two edits from a real one. `ReferenceCodeMatcher` slides the code over the memo and scores the closest run, so a chatty memo costs nothing and a one-character slip stays a distance of 1.

Amount and timing are tiebreakers only, and deliberately so: every buyer of the same tier pays the identical amount, so amount alone is nearly worthless as evidence, and a bank's name for an account routinely isn't the buyer's name on the order.

**Nothing is matched automatically.** The suggestions carry their evidence — code distance, whether the amount agrees, whether the payment landed inside the hold window — so an operator can check the claim rather than defer to a score. This is the same rule `EtransferConfirmationService` already follows: never settle on a partial match, and a near-miss on eight characters is exactly that.

Linking goes through `PaymentConfirmationService.confirmPayment`, not a bespoke transition. That matters because these orders have usually expired while the payment sat in the queue, and `confirmPayment` already re-acquires the seats or falls to `REFUND_PENDING` when they've been resold. A direct `setStatus(PAID)` here would issue tickets for seats somebody else has bought.

Payments that will never match — a transfer sent to the wrong organisation, a duplicate owed no tickets — are dismissed rather than left to rot at the top of the queue, which is what `dismissed_at` is for. A queue that always has junk in it is a queue people stop reading.
