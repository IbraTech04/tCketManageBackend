package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.OrderItemRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.PaymentContext;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProviderRegistry;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Owns the order lifecycle: creation (with inventory reservation, server-side pricing, and payment
 * initiation), retrieval, and cancellation.
 */
@Service
@AllArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final InventoryService inventoryService;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentConfirmationService confirmationService;

    public OrderCreationResult createOrder(CreateOrderRequest request) {
        PaymentProvider provider = providerRegistry.resolve(request.getProviderId());

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyerEmail(request.getBuyerEmail())
                .event(event)
                .status(OrderStatus.AWAITING_PAYMENT)
                .providerId(provider.id())
                .referenceCode(generateReferenceCode())
                .currency("CAD")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(provider.holdDuration()))
                .items(new ArrayList<>())
                .build();

        BigDecimal amountTotal = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            TicketType ticketType = ticketTypeRepository.findById(itemReq.getTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "TicketType not found: " + itemReq.getTicketTypeId()));
            if (ticketType.getEvent() == null || !ticketType.getEvent().getId().equals(event.getId())) {
                throw new IllegalArgumentException(
                    "Ticket type " + ticketType.getId() + " does not belong to event " + event.getId());
            }

            // Reserve one seat; throws ConflictException if sold out.
            inventoryService.reserve(ticketType.getId(), 1);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .ticketType(ticketType)
                    .attendeeFirstName(itemReq.getAttendeeFirstName())
                    .attendeeLastName(itemReq.getAttendeeLastName())
                    .attendeeEmail(itemReq.getAttendeeEmail())
                    .unitPrice(ticketType.getPrice())
                    .build());
            amountTotal = amountTotal.add(ticketType.getPrice());
        }

        order.setAmountTotal(amountTotal);
        order = orderRepository.save(order);

        PaymentContext context = new PaymentContext(
                order.getId(), order.getReferenceCode(), order.getAmountTotal(), order.getCurrency(),
                order.getBuyerEmail(), "Tickets for " + event.getName(), null, null);
        PaymentInitiation initiation = provider.initiate(context);
        order.setProviderRef(initiation.providerRef());
        order = orderRepository.save(order);

        // Providers that settle synchronously (e.g. Mock auto-pay) confirm right away.
        if (initiation instanceof PaymentInitiation.Completed) {
            order = confirmationService.confirmPayment(order.getId(), initiation.providerRef());
        }

        return new OrderCreationResult(order, initiation);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public Order cancelOrder(UUID id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ConflictException("Only orders awaiting payment can be cancelled (status="
                    + order.getStatus() + ")");
        }
        releaseInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /** Releases all seats held by an order back to inventory. */
    void releaseInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryService.release(item.getTicketType().getId(), 1);
        }
    }

    private String generateReferenceCode() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
