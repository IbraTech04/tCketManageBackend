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