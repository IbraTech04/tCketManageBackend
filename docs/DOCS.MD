# tCketManage Configuration Guide

A guide to configuring tCketManage: what each setting does and how to turn features on and off. All settings live in `application.properties` (or `application-{profile}.properties` for per-environment bindings). For a copy-paste starting 
point, see `application.properties.example`.

## Database

> [!NOTE]
> We do not currently use any automated migration tools and rely on `ddl-auto=update` to keep the schema in sync with the code.

By default the app runs on **SQLite** (using the `tcketmanage.db` file). For any real deployment use **PostgreSQL** (Or really, any production-grade RDBMS supported by Spring Data JPA). SQLite serializes everything, so oversell protection
and concurrency don't apply. This project is designed for PostgresSQL and tested primarily on it, so while SQLite works for local dev and testing, it's not recommended for production.

To switch to PostgreSQL, configure the datasource URL and credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/tcketmanage
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=tcket
spring.datasource.password={your_password}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

| Setting | What it does |
|---|---|
| `spring.jpa.hibernate.ddl-auto` | Schema handling. `update` (default) auto-creates/alters tables; use `validate` with migrations for stricter prod control. |
| `spring.datasource.hikari.maximum-pool-size` | Max DB connections (prod). Default `20`. Keep it a bit above your inbound concurrency so the expiry sweep and email pool aren't starved. |
| `spring.datasource.hikari.transaction-isolation` | Leave at `READ_COMMITTED`. Concurrency safety comes from explicit row locks, not isolation — raising it to `SERIALIZABLE` causes un-retried failures. |

> [!WARNING]
> **Don't set `spring.jpa.open-in-view=false`.** Read endpoints map entities to DTOs after the
> transaction commits and rely on the default (`true`); `false` breaks most reads. Decoupling this
> is on the roadmap but for now, leave it as-is

## Payments

Payments are a big part of tCketManage, and the backend is designed to be flexible and support multiple providers to match

tCketManage supports three payment providers:

- `mock`: A "fake" provider for local development and testing. It simulates payment flows without real money by automatically confirming orders after a configurable delay.
- `interac`: The real Interac e-Transfer flow, which is popular in Canada. Listens to an email inbox for payment notifications and confirms orders based on matching reference codes and amounts.
- `stripe`: A stub for future Stripe integration; enabling it currently throws an exception until the real implementation is complete.

Pick the active provider, then configure it. The provider an order uses (when it doesn't ask for a specific one) is set by:

```properties
payments.default-provider=mock      # mock | interac | stripe
```

The expired-order sweep runs on a timer; lower it to expire unpaid holds sooner:

```properties
payments.sweep-interval-ms=60000    # default 60s
```

### Mock provider (dev/testing)

A fake provider for local work — no real money.

```properties
payments.mock.enabled=true
payments.mock.auto-confirm=true     # true: orders settle instantly. false: wait like a real flow
payments.mock.hold-minutes=30       # how long an unpaid order is held before expiry
```

### Interac e-Transfer

The real payment path. **Turn it on:**

```properties
payments.default-provider=interac
payments.interac.enabled=true
payments.interac.payee-email=you@yourdomain.com   # required; shown to buyers
payments.interac.hold-hours=48                     # e-transfers settle slowly, hence hours
```

This alone gives you the **manual flow**: buyers send an e-Transfer with a reference code, and an operator confirms the order via the admin endpoint.

**Auto-confirmation (IMAP listener):** to have the app watch a mailbox and settle orders
automatically as deposit emails arrive, enable the IMAP listener. It parses each Interac
notification, matches the memo to an order's reference code, checks the amount, and confirms — moving
anything it can't cleanly match to a review folder for an operator.

```properties
payments.interac.imap.enabled=true
payments.interac.imap.host=imap.yourprovider.com
payments.interac.imap.port=993
payments.interac.imap.username=you@yourdomain.com
payments.interac.imap.password=...
payments.interac.imap.folder=INBOX
payments.interac.imap.review-folder=NeedsReview
# Comma-separated From addresses trusted as genuine Interac mail; everything else is quarantined.
payments.interac.imap.expected-senders=notify@payments.interac.ca
# Require the deposit amount to match the order total exactly (recommended).
payments.interac.imap.require-exact-amount=true
```

**DMARC hardening (optional):** in addition to the trusted-sender list, you can require that mail
pass DMARC, read from the `Authentication-Results` header your own mail server stamps. Off by
default (so providers that don't stamp the header still work); turn it on for extra spoofing
protection:

```properties
payments.interac.imap.dmarc.enabled=true
payments.interac.imap.dmarc.authserv-id=mail.yourdomain.com   # required when enabled
payments.interac.imap.dmarc.aligned-domain=                   # optional; defaults to the From domain
```

## Email

Controls ticket delivery. By default email is **log-only.** Tickets are rendered and logged but not sent, so you need no SMTP for local work.

To configure real email sending, set the SMTP properties and turn it on:

```properties
app.email.enabled=true
app.email.from=tickets@yourdomain.com
app.email.from-name=Your Event

# SMTP transport (port 465 = implicit TLS / SMTPS)
spring.mail.host=smtp.yourprovider.com
spring.mail.port=465
spring.mail.username=tickets@yourdomain.com
spring.mail.password=...
spring.mail.properties.mail.smtp.ssl.enable=true
```

> [!NOTE]
> Using a STARTTLS provider on port 587? Set `spring.mail.port=587`,
> `spring.mail.properties.mail.smtp.ssl.enable=false`, and
> `spring.mail.properties.mail.smtp.starttls.enable=true`.

By default, email sending is asynchronous with a small thread pool, so it doesn't block order processing. The pool size and queue capacity are configurable:

```properties
app.email.async.concurrency=3       # worker threads
app.email.async.queue-capacity=500  # pending sends before the caller throttles inline
```

## API docs (Swagger)

Enabled by default at `/swagger-ui.html`. Disable in production if you don't want it exposed (which is recommended):

```properties
springdoc.swagger-ui.enabled=false
```

## CORS & WebSocket

Browser origins allowed to call `/api/**` and connect to the `/ws` WebSocket. The `*` default is dev-only. You should absolutely restrict this in production to your frontend's actual origin(s): 

```properties
web.cors.allowed-origins=https://app.yourdomain.com
```

The WebSocket endpoint (`/ws`, STOMP) pushes live bulk-email progress to operators; clients subscribe to `/topic/email-jobs/{jobId}`. No properties to configure beyond the CORS origins above.

## Signing keys

Ticket QR codes are signed with **Ed25519**. The keypair is loaded from the classpath, not from
properties — place them at:

- `src/main/resources/keys/private.pem` — signs tickets 
- `src/main/resources/keys/public.pem` — verifies scans

The app won't start without them. Generate a fresh pair per deployment and never reuse dev keys in production.

If you're on Linux, you can generate them with:

```bash
openssl genpkey -algorithm Ed25519 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem
mv private.pem src/main/resources/keys/
mv public.pem src/main/resources/keys/
```
