package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;

import java.util.UUID;

/**
 * Published whenever an order reaches a state the buyer should hear about but which issues no
 * tickets — expiry, cancellation, quarantine, or a payment queued for refund. Mail is sent off the
 * back of this <em>after the publishing transaction commits</em> (see
 * {@link OrderNotificationListener}), for the same reasons as {@link TicketsIssuedEvent}: the async
 * sender re-loads the order in its own transaction and must see committed rows, and no SMTP work may
 * happen inside a transaction that holds the order row lock.
 *
 * <p>Carrying the id rather than the entity is deliberate — the entity would be stale (and, for the
 * lazily-loaded items, detached) by the time the listener runs.
 */
public record OrderNotificationEvent(UUID orderId, OrderNotification notification) {}
