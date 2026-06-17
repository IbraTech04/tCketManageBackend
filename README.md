# tCketManage Backend

REST API backend for tCketManage — a lightweight, self-hostable event ticketing system. Handles event management, ticket generation, order/payment processing, QR-based ticket scanning, and email delivery.

Built with **Spring Boot 3.5 + Java 21**, backed by PostgreSQL and designed for high concurrency and data integrity.

## Features

- **Events & Zones:** Create events with named access zones; ticket types carry per-zone entitlements and entry limits.
- **Ticket Types:** Define multiple ticket types per event with different prices, zone access, and availability windows (coming soon).
- **Orders & Payments:** Pluggable payment provider system with support for Interac E-Transfer (See [ETRANSFER.MD](./ETRANSFER.MD)). Stripe integration is on the roadmap.
- **Ticket generation and MailMerge:** Tickets are signed with ED25519, embedded in a QR code, and delivered via email using an HTML/SVG template
- **QR scanning:** Scan endpoint validates a ticket's cryptographic signature and records scan events per zone
- **CSV import** — bulk-import attendees from CSV with configurable column mapping

## Quick Start

First, copy the example `applications.properties.example` to `application.properties` and adjust any settings as needed (e.g. payment provider, email config, database URL). For local development, the defaults should work out of the box.

```bash
./mvnw spring-boot:run
```

The app starts on port 8080 with SQLite (`tcketmanage.db`) and a mock payment provider that auto-confirms orders. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `payments.default-provider` | `mock` | Active payment provider (`mock`, `stripe`, `interac`) |
| `payments.mock.auto-confirm` | `true` | Settle mock orders immediately |
| `payments.interac.payee-email` | _(empty)_ | Required to enable Interac e-Transfer |
| `app.email.enabled` | `true` | `false` logs emails only; `true` sends via SMTP |
| `spring.datasource.url` | SQLite | Switch to `jdbc:postgresql://...` for production |

## API Overview

All routes are under `/api/v1/`.

| Resource | Endpoints |
|---|---|
| Events | `GET/POST /events`, `GET/PUT/DELETE /events/{id}`, `POST /events/full` (atomic wizard) |
| Zones | Sub-resource of events + `GET/PUT/DELETE /zones/{id}` |
| Ticket Types | Sub-resource of events + `PUT/DELETE /ticket-types/{id}` |
| Tickets | `GET/POST /tickets`, `GET/PUT/DELETE /tickets/{id}`, `GET /events/{id}/tickets` |
| Orders | `GET/POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`, `POST /orders/{id}/confirm-manual-payment` |
| Scanning | `POST /scan` |
| CSV Import | `POST /events/{id}/imports` (multipart, admin-only) |
| Payment webhooks | `POST /webhooks/payment` |

## Concurrency & Locking Strategy

Selling tickets is a classic oversell problem: many buyers can race for the last seat of a ticket type, and the same order can receive a confirmation, a buyer-cancel, and an expiry sweep at nearly the same moment. The majority of the time spent architecting tCketManage was designing a locking strategy that enables high concurrency without risking oversells or invalid states, and without holding large locks for large periods of time. If you'd like to learn more about the approach I took, check out [LOCKING.MD](./DOCS/LOCKING.MD).

## Tech Stack

- Spring Boot 3.5, Spring Data JPA, Spring Mail
- SQLite (dev) / PostgreSQL (prod)
- ZXing (QR generation), Batik (SVG rendering)
- Thymeleaf (email and ticket templates)
- Lombok, springdoc-openapi

## Still To Do

- Authentication/authorization: operator endpoints carry `@PreAuthorize` checks against three configurable roles (`tcketmanage.security.roles.{scanner,event-manager,admin}`, default `SCANNER`/`EVENT_MANAGER`/`ADMIN` — remap onto a host's own roles via config). These are **inert unless a host enables Spring method security**. The standalone `tcketmanage-app` enables nothing, so all endpoints are open (for dev/testing); an embedding host (e.g. LensBridge) enables enforcement and provides the identities/role hierarchy.
- Stripe payment implementation
- Ticket theming w/ AI designer
- Move to Spring Boot 4 + Java 25?
- Anything in [Plan.md](./DOCS/Plan.md)
- Upgrade to Spring Boot 4 and Java 25?  
- Anything else that's broken or missing! This is a very early-stage project, so expect rough edges. Contributions welcome!


## Epilogue

tCketManage started as a PoC I built in highschool to make a simple ticketing system for school events. The original version used Python and tkinter and was a desktop app that ran on the organizer's computer, which was a nightmare to maintain and distribute. Worse, it used Google Sheets as the backend "database" (decision made so we could easily revert back to manual ticketing if the app broke during an event), which led to me eventually writing my own ORM layer on top of the Sheets API to manage JOINs and whatnot.

The new version is a complete rewrite from the ground up in Java with Spring Boot, designed to be a wrapped by anything from a simple command-line script to a web app (or a PWA). This was designed to address one of the main pain points of the original app, which was the requirement of a Windows-based laptop to run the organizer's interface. The new version can run on any platform that supports Java, and the API can be consumed by any frontend framework.
