package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.payment.PaymentContext;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProviderRegistry;
import com.ibrasoft.tcketmanagebackend.properties.OrderProperties;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the order lifecycle: creation (with inventory reservation, server-side pricing, and payment
 * initiation), retrieval, and cancellation.
 */
@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final InventoryService inventoryService;
    private final PaymentProviderRegistry providerRegistry;
    private final OrderTransactions orderTransactions;
    private final OrderOwnerResolver ownerResolver;
    private final OrderProperties orderProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates an order. Deliberately NOT {@code @Transactional}: the inventory hold is committed in
     * {@link OrderTransactions#reserveAndPersist} BEFORE the payment provider is contacted, so the
     * pessimistic ticket-type row lock is never held across {@link PaymentProvider#initiate}'s
     * (potentially slow) network call. The provider ref — and synchronous confirmation, if any — is
     * then recorded in a second transaction.
     */
    public OrderCreationResult createOrder(CreateOrderRequest request) {
        // Capture who this order belongs to (host-provided; null = anonymous/guest). Resolved on the
        // request thread before any DB work so a require-owner deployment fails fast without reserving
        // inventory. The value is opaque to core — see OrderOwnerResolver.
        String ownerRef = ownerResolver.resolveOwnerRef(request);
        if (ownerRef == null && orderProperties.isRequireOwner()) {
            throw new SecurityException("This deployment requires an identified order owner");
        }

        PaymentProvider provider = providerRegistry.resolve(request.getProviderId());

        // Phase 1 (committed): reserve seats + persist the pending order, then release the row lock.
        Order order = orderTransactions.reserveAndPersist(request, provider, ownerRef);

        // If it's free, no need to go thru the rest of the flow
        if (isFree(order)) {
            PaymentInitiation initiation =
                    new PaymentInitiation.Completed("free-" + order.getReferenceCode());
            return new OrderCreationResult(
                    orderTransactions.finalizeInitiation(order.getId(), initiation), initiation);
        }

        // Phase 2 (no transaction, no lock held): talk to the payment provider.
        PaymentContext context = new PaymentContext(
                order.getId(), order.getReferenceCode(), order.getAmountTotal(), order.getCurrency(),
                order.getBuyerEmail(), "Tickets for " + order.getEvent().getName(), null, null);
        PaymentInitiation initiation;
        try {
            initiation = provider.initiate(context);
        } catch (RuntimeException e) {
            // Provider failed to begin payment — release the hold instead of stranding it.
            orderTransactions.releaseHold(order.getId());
            throw e;
        }

        // Phase 3 (committed): record the provider ref and confirm if it settled synchronously.
        order = orderTransactions.finalizeInitiation(order.getId(), initiation);
        return new OrderCreationResult(order, initiation);
    }

    private boolean isFree(Order order) {
        return order.getAmountTotal() != null && order.getAmountTotal().signum() == 0;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    /**
     * Orders for an event, optionally filtered by status. The status filter backs the operator
     * review queues (e.g. {@code QUARANTINED} orders awaiting an approve/deny decision).
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByEvent(UUID eventId, OrderStatus status) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return status == null
                ? orderRepository.findByEventId(eventId)
                : orderRepository.findByEventIdAndStatus(eventId, status);
    }

    /** Operator/support lookup of all orders tagged with a host-owned {@code externalRef}. */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByExternalRef(String externalRef) {
        return orderRepository.findByExternalRef(externalRef);
    }

    /**
     * Operator lookup of a single order by the {@code XXXX-XXXX} code a buyer would have quoted.
     *
     * <p>Exists for resolving an unmatched payment by hand: when the memo carried nothing the matcher
     * could work with, an operator identifies the order themselves — from the buyer, the amount, the
     * timing — and names it by the code they can see in the order book. Without this the manual path
     * would have to ask for a UUID, which is not a thing anyone has in front of them.
     *
     * <p>Normalizes case and surrounding whitespace, since the code is being retyped or pasted.
     *
     * @return the order, or empty when no such code exists — a miss is an ordinary answer here, not
     *         an error, because the operator is being told they got the code wrong
     */
    @Transactional(readOnly = true)
    public Optional<Order> findByReferenceCode(String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return Optional.empty();
        }
        return orderRepository.findByReferenceCode(referenceCode.trim().toUpperCase());
    }

    @Transactional
    public Order cancelOrder(UUID id) {
        // Lock the order row so a concurrent expiry sweep can't also release the same hold.
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ConflictException("Only orders awaiting payment can be cancelled (status="
                    + order.getStatus() + ")");
        }
        releaseInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        Order cancelled = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderNotificationEvent(id, OrderNotification.CANCELLED));
        return cancelled;
    }

    /** Releases all seats held by an order back to inventory. Runs within the caller's transaction. */
    void releaseInventory(Order order) {
        inventoryService.releaseAll(InventoryService.seatsByTicketType(order.getItems()));
    }
}
