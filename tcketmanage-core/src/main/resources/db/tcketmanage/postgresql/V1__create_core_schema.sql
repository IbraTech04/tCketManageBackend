-- Core ticketing schema, mirroring the JPA mappings in com.ibrasoft.tcketmanagebackend.model.*.
-- Table names carry a "tcket:" prefix so they can't collide with a host application's own
-- tables; the colon isn't a valid unquoted Postgres identifier character, hence the quoting
-- below (this matches what Hibernate itself emits for these entities).
--
-- Every instant-valued column is timestamptz, not timestamp. Hibernate 6 maps java.time.Instant
-- to TIMESTAMP_UTC, which is "timestamp with time zone" on PostgreSQL; a plain timestamp column
-- fails ddl-auto=validate, and under ddl-auto=update it is worse — Hibernate leaves the column
-- alone and every value round-trips shifted by the server's session offset, which would quietly
-- mis-time the orders.expires_at sweep in OrderExpiryService.

CREATE TABLE "tcket:events" (
    id          uuid         NOT NULL,
    name        varchar(255) NOT NULL,
    time        timestamptz  NOT NULL,
    location    varchar(255) NOT NULL,
    description varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE "tcket:zones" (
    id          uuid        NOT NULL,
    event_id    uuid,
    name        varchar(20) NOT NULL,
    description varchar(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_zones_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE TABLE "tcket:ticket_types" (
    id             uuid          NOT NULL,
    event_id       uuid          NOT NULL,
    name           varchar(100)  NOT NULL,
    price          numeric(10,2) NOT NULL,
    is_active      boolean       NOT NULL,
    created_at     timestamptz   NOT NULL,
    sales_start_at timestamptz,
    sales_end_at   timestamptz,
    capacity       integer,
    reserved_count integer       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE TABLE "tcket:zone_entitlements" (
    id             uuid NOT NULL,
    ticket_type_id uuid NOT NULL,
    zone_id        uuid NOT NULL,
    max_entries    integer CHECK (max_entries >= 1),
    PRIMARY KEY (id),
    CONSTRAINT uk_zone_entitlement_type_zone UNIQUE (ticket_type_id, zone_id),
    CONSTRAINT fk_zone_entitlements_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id),
    CONSTRAINT fk_zone_entitlements_zone FOREIGN KEY (zone_id) REFERENCES "tcket:zones" (id)
);

CREATE TABLE "tcket:orders" (
    id             uuid          NOT NULL,
    buyer_email    varchar(255)  NOT NULL,
    external_ref   varchar(200),
    event_id       uuid          NOT NULL,
    status         varchar(20)   NOT NULL
                       CHECK (status IN ('AWAITING_PAYMENT','PAID','EXPIRED','CANCELLED','REFUND_PENDING','REFUNDED','QUARANTINED')),
    provider_id    varchar(40)   NOT NULL,
    provider_ref   varchar(255),
    reference_code varchar(32)   NOT NULL,
    amount_total   numeric(10,2) NOT NULL,
    currency       varchar(3)    NOT NULL,
    created_at     timestamptz   NOT NULL,
    expires_at     timestamptz,
    paid_at        timestamptz,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_reference_code UNIQUE (reference_code),
    CONSTRAINT fk_orders_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE INDEX idx_order_external_ref ON "tcket:orders" (external_ref);

CREATE TABLE "tcket:order_items" (
    id                  uuid          NOT NULL,
    order_id            uuid          NOT NULL,
    ticket_type_id      uuid          NOT NULL,
    attendee_first_name varchar(255)  NOT NULL,
    attendee_last_name  varchar(255)  NOT NULL,
    attendee_email      varchar(255)  NOT NULL,
    unit_price          numeric(10,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id),
    CONSTRAINT fk_order_items_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id)
);

CREATE TABLE "tcket:tickets" (
    id               uuid         NOT NULL,
    event_id         uuid,
    first_name       varchar(50)  NOT NULL,
    last_name        varchar(50)  NOT NULL,
    email            varchar(255) NOT NULL,
    ticket_type_id   uuid         NOT NULL,
    holder_ref       varchar(200),
    status           varchar(20) CHECK (status IN ('ACTIVE','CANCELLED')),
    order_id         uuid,
    last_ticket_sent timestamptz,
    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id),
    CONSTRAINT fk_tickets_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id),
    CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id)
);

CREATE INDEX idx_ticket_holder_ref ON "tcket:tickets" (holder_ref);

CREATE TABLE "tcket:scan_events" (
    id        uuid      NOT NULL,
    ticket_id uuid      NOT NULL,
    zone_id   uuid,
    timestamp timestamptz NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_scan_events_zone FOREIGN KEY (zone_id) REFERENCES "tcket:zones" (id)
);

CREATE INDEX idx_scan_ticket_zone ON "tcket:scan_events" (ticket_id, zone_id);
