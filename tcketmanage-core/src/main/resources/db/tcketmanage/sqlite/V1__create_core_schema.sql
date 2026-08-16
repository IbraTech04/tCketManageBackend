-- Core ticketing schema, mirroring the JPA mappings in com.ibrasoft.tcketmanagebackend.model.*.
-- Table names carry a "tcket:" prefix so they can't collide with a host application's own
-- tables; the colon isn't a valid unquoted Postgres identifier character, hence the quoting
-- below (this matches what Hibernate itself emits for these entities).
--
-- SQLite twin of db/tcketmanage/postgresql/V1__create_core_schema.sql: uuid -> blob (SQLite has no uuid
-- type, and Hibernate maps java.util.UUID to BINARY there, same as every other UUID column a
-- host application like LensBridge declares for its own SQLite migrations), timestamptz ->
-- timestamp, numeric(10,2) -> numeric (SQLite ignores precision/scale; kept unspecified rather
-- than carrying digits that mean nothing under NUMERIC affinity).

CREATE TABLE "tcket:events" (
    id          blob         NOT NULL,
    name        varchar(255) NOT NULL,
    time        timestamp    NOT NULL,
    location    varchar(255) NOT NULL,
    description varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE "tcket:zones" (
    id          blob        NOT NULL,
    event_id    blob,
    name        varchar(20) NOT NULL,
    description varchar(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_zones_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE TABLE "tcket:ticket_types" (
    id             blob         NOT NULL,
    event_id       blob         NOT NULL,
    name           varchar(100) NOT NULL,
    price          numeric      NOT NULL,
    is_active      boolean      NOT NULL,
    created_at     timestamp    NOT NULL,
    sales_start_at timestamp,
    sales_end_at   timestamp,
    capacity       integer,
    reserved_count integer      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE TABLE "tcket:zone_entitlements" (
    id             blob NOT NULL,
    ticket_type_id blob NOT NULL,
    zone_id        blob NOT NULL,
    max_entries    integer CHECK (max_entries >= 1),
    PRIMARY KEY (id),
    CONSTRAINT uk_zone_entitlement_type_zone UNIQUE (ticket_type_id, zone_id),
    CONSTRAINT fk_zone_entitlements_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id),
    CONSTRAINT fk_zone_entitlements_zone FOREIGN KEY (zone_id) REFERENCES "tcket:zones" (id)
);

CREATE TABLE "tcket:orders" (
    id             blob         NOT NULL,
    buyer_email    varchar(255) NOT NULL,
    external_ref   varchar(200),
    event_id       blob         NOT NULL,
    status         varchar(20)  NOT NULL
                       CHECK (status IN ('AWAITING_PAYMENT','PAID','EXPIRED','CANCELLED','REFUND_PENDING','REFUNDED','QUARANTINED')),
    provider_id    varchar(40)  NOT NULL,
    provider_ref   varchar(255),
    reference_code varchar(32)  NOT NULL,
    amount_total   numeric      NOT NULL,
    currency       varchar(3)   NOT NULL,
    created_at     timestamp    NOT NULL,
    expires_at     timestamp,
    paid_at        timestamp,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_reference_code UNIQUE (reference_code),
    CONSTRAINT fk_orders_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id)
);

CREATE INDEX idx_order_external_ref ON "tcket:orders" (external_ref);

CREATE TABLE "tcket:order_items" (
    id                  blob         NOT NULL,
    order_id            blob         NOT NULL,
    ticket_type_id      blob         NOT NULL,
    attendee_first_name varchar(255) NOT NULL,
    attendee_last_name  varchar(255) NOT NULL,
    attendee_email      varchar(255) NOT NULL,
    unit_price          numeric      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id),
    CONSTRAINT fk_order_items_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id)
);

CREATE TABLE "tcket:tickets" (
    id               blob         NOT NULL,
    event_id         blob,
    first_name       varchar(50)  NOT NULL,
    last_name        varchar(50)  NOT NULL,
    email            varchar(255) NOT NULL,
    ticket_type_id   blob         NOT NULL,
    holder_ref       varchar(200),
    status           varchar(20) CHECK (status IN ('ACTIVE','CANCELLED')),
    order_id         blob,
    last_ticket_sent timestamp,
    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_event FOREIGN KEY (event_id) REFERENCES "tcket:events" (id),
    CONSTRAINT fk_tickets_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES "tcket:ticket_types" (id),
    CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES "tcket:orders" (id)
);

CREATE INDEX idx_ticket_holder_ref ON "tcket:tickets" (holder_ref);

CREATE TABLE "tcket:scan_events" (
    id        blob      NOT NULL,
    ticket_id blob      NOT NULL,
    zone_id   blob,
    timestamp timestamp NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_scan_events_zone FOREIGN KEY (zone_id) REFERENCES "tcket:zones" (id)
);

CREATE INDEX idx_scan_ticket_zone ON "tcket:scan_events" (ticket_id, zone_id);
