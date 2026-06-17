package com.ibrasoft.tcketmanagebackend.service.order;

import java.util.List;
import java.util.UUID;

/**
 * Published by {@link FulfillmentService} once an order's tickets are persisted. Email dispatch keys
 * off this <em>after the transaction commits</em>, so the async sender (which re-loads each ticket in
 * its own transaction) is guaranteed to see committed rows, and SMTP work never runs inside the
 * order-confirmation transaction.
 */
public record TicketsIssuedEvent(List<UUID> ticketIds) {}
