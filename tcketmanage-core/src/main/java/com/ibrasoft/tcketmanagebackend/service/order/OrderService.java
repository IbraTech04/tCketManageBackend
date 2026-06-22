package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.payment.PaymentContext;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProviderRegistry;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    /**
     * Creates an order. Deliberately NOT {@code @Transactional}: the inventory hold is committed in
     * {@link OrderTransactions#reserveAndPersist} BEFORE the payment provider is contacted, so the
     * pessimistic ticket-type row lock is never held across {@link PaymentProvider#initiate}'s
     * (potentially slow) network call. The provider ref — and synchronous confirmation, if any — is
     * then recorded in a second transaction.
     */
    public OrderCreationResult createOrder(CreateOrderRequest request) {
        PaymentProvider provider = providerRegistry.resolve(request.getProviderId());

        // Phase 1 (committed): reserve seats + persist the pending order, then release the row lock.
        Order order = orderTransactions.reserveAndPersist(request, provider);

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

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByEvent(UUID eventId) {
        return getOrdersByEvent(eventId, null);
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
        return orderRepository.save(order);
    }

    /** Releases all seats held by an order back to inventory. Runs within the caller's transaction. */
    void releaseInventory(Order order) {
        inventoryService.releaseAll(InventoryService.seatsByTicketType(order.getItems()));
    }
}
