# tCketManage Backend - Interac E-Transfer Payment Flow

Many small school clubs rely on Interac E-Transfer for ticket sales, but unlike a system like Stripe, E-Transfers are not designed to integrate with third-party applications. There are no official APIs (and anything unofficial is legally dubious at best), which means event organizers have to manually confirm and monitor for incoming payments, and then manually mark orders as paid in the system. This is the main issue tCketManage aims to solve with its E-Transfer payment provider. 

## Payment Flow

At its core, every tCketManage order has a unique confirmation code in the form `XXXX-XXXX` that the buyer includes in the message of their E-Transfer. Using IMAP, tCketManage monitors the inbox of the email address configured in `payments.interac.payee-email` for incoming E-Transfer notifications. When a new email arrives, the system:

1. Confirms the sender is a valid Interac notification
2. Parses the email to extract the confirmation code
3. Matches it to an existing order, and if a match is found
4. Marks the order as paid and sends a confirmation email to the buyer
5. If the email cannot be parsed or matched, it is moved to a quarantine folder for manual review by the event organizer. In the future this will also be flagged by the frontend admin dashboard