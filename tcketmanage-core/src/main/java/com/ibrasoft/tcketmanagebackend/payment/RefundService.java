package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Refunds a {@code PAID} order: voids its issued tickets, returns their seats to inventory, and asks
 * the payment provider to send the money back. Automatic providers (e.g. Stripe) complete in-band and
 * settle the order to {@code REFUNDED}; manual providers (e.g. Interac) signal that an operator must
 * pay the refund out of band, leaving the order {@code REFUND_PENDING} until
 * {@link #markRefundComplete} confirms the money was sent.
 *
 * <p>The order row is locked for the whole transition, so the ticket voiding, seat release, and
 * provider call are atomic with the status change: if the provider refund throws, nothing is voided
 * and the order stays {@code PAID}.
 */
@Service
@AllArgsConstructor
public class RefundService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final InventoryService inventoryService;
    private final PaymentProviderRegistry providerRegistry;

    /**
     * Refunds a paid order. Idempotent: an order already {@code REFUNDED} or {@code REFUND_PENDING} is
     * returned untouched; any non-{@code PAID} status is rejected.
     */
    @Transactional
    public Order refundOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.REFUND_PENDING) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ConflictException(
                "Only a PAID order can be refunded (order " + orderId + " is " + order.getStatus() + ")");
        }

        voidTicketsAndReleaseSeats(order);

        // Throws on an unimplemented/failed provider refund, rolling back the voiding above.
        PaymentProvider provider = providerRegistry.resolve(order.getProviderId());
        RefundOutcome outcome = provider.refund(order);

        order.setStatus(outcome.succeeded() ? OrderStatus.REFUNDED : OrderStatus.REFUND_PENDING);
        return orderRepository.save(order);
    }

    /**
     * Operator confirmation that a manual ({@code REFUND_PENDING}) refund has been paid out of band.
     * Idempotent: an already-{@code REFUNDED} order is a no-op; any other status is rejected.
     */
    @Transactional
    public Order markRefundComplete(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.REFUNDED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.REFUND_PENDING) {
            throw new ConflictException(
                "Only a REFUND_PENDING order can be completed (order " + orderId + " is "
                    + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.REFUNDED);
        return orderRepository.save(order);
    }

    /**
     * Cancels every still-{@code ACTIVE} ticket of the order and releases its seat. Tickets already
     * revoked/cancelled are skipped — their seats were released when they left {@code ACTIVE}, so this
     * never double-releases.
     */
    private void voidTicketsAndReleaseSeats(Order order) {
        List<Ticket> tickets = ticketRepository.findByOrder_Id(order.getId());
        for (Ticket ticket : tickets) {
            TicketStatus status = ticket.getStatus() == null ? TicketStatus.ACTIVE : ticket.getStatus();
            if (status != TicketStatus.ACTIVE) {
                continue;
            }
            if (ticket.getTicketType() != null) {
                inventoryService.release(ticket.getTicketType().getId(), 1);
            }
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.save(ticket);
        }
    }
}
