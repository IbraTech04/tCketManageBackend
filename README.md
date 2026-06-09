# tCketManage Backend

REST API backend for tCketManage — a lightweight, self-hostable event ticketing system. Handles event management, ticket generation, order/payment processing, QR-based ticket scanning, and email delivery.

Built with **Spring Boot 3.5 + Java 21**, backed by SQLite (dev) or PostgreSQL (production).

## Features

- **Events & Zones** — create events with named access zones; ticket types carry per-zone entitlements
- **Orders & Payments** — pluggable payment provider system with Mock (auto-confirm), Stripe (stub), and Interac e-Transfer (manual reference-code flow); expired orders are swept automatically
- **Ticket generation** — tickets are signed with ED25519, embedded in a QR code, and delivered via email using an HTML/SVG template
- **QR scanning** — scan endpoint validates a ticket's cryptographic signature and records scan events per zone
- **CSV import** — bulk-import attendees from CSV with configurable column mapping
- **Swagger UI** — interactive API docs at `/swagger-ui.html`; OpenAPI spec at `/api-docs`

## Quick Start

```bash
./mvnw spring-boot:run
```

The app starts on port 8080 with SQLite (`tcketmanage.db`) and a mock payment provider that auto-confirms orders. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `payments.default-provider` | `mock` | Active payment provider (`mock`, `stripe`, `interac`) |
| `payments.admin-token` | `changeme-dev-token` | Shared secret for admin-only endpoints |
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

Selling tickets is a classic oversell problem: many buyers can race for the last seat of a ticket type, and the same order can receive a confirmation, a buyer-cancel, and an expiry sweep at nearly the same moment. The majority of the time spent architecting tCketManage was designing a locking strategy that enables high concurrency without risking oversells or invalid states, and without holding large locks for large periods of time. If you'd like to learn more about the approach I took, check out [LOCKING.MD](LOCKING.MD).

## Tech Stack

- Spring Boot 3.5, Spring Data JPA, Spring Mail
- SQLite (dev) / PostgreSQL (prod)
- ZXing (QR generation), Batik (SVG rendering)
- Thymeleaf (email and ticket templates)
- Lombok, springdoc-openapi

## Still To Do

- Authentication (JWT/OAuth — currently all endpoints are open except those behind `X-Admin-Token`)
- Stripe payment implementation 
- ETransfer payment confirmation flow
- Docker / deployment packaging
- STOMP/WebSocket support for async updates (specifically email delivery status)
- Anything else that's broken or missing! This is a very early-stage project, so expect rough edges. Contributions welcome!
- Ticket theming + new Ticket Designs :eyes:
