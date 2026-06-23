package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.OrderItemRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The committed transaction boundaries for order creation, deliberately split
 * into a separate bean
 * so the surrounding {@link OrderService#createOrder} orchestration can run
 * WITHOUT a transaction —
 * and therefore without holding the pessimistic ticket-type row lock — across
 * the external
 * {@link PaymentProvider#initiate} network call. Routing these calls through a
 * distinct Spring bean
 * is what makes the {@code @Transactional} boundaries (and the lock release
 * between them) actually
 * take effect; a self-invocation inside {@code OrderService} would bypass the
 * proxy and not.
 */
@Service
@AllArgsConstructor
class OrderTransactions {

    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final InventoryService inventoryService;
    private final PaymentConfirmationService confirmationService;

    /**
     * The alphabet for generating human-friendly reference codes. Excludes easily
     * confused chars (1 vs I, 0
     * vs O) and is all uppercase to avoid case sensitivity issues. With 32 chars, 8
     * random chars gives us
     * 32^8 = 1 trillion combinations, which is plenty for our scale and short
     * enough to be memorable and typeable. We prefix with "ORD-" to make it clear
     * these are order codes, and to provide a fixed length for easier parsing in
     * emails and such.
     */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();


    /**
     * Phase 1: validate, reserve every seat, and persist the order as
     * {@code AWAITING_PAYMENT} in a
     * single transaction. Reservations are all-or-nothing — a sold-out item throws
     * and rolls the
     * whole order back. The transaction commits on return, releasing the
     * ticket-type row locks
     * before the caller contacts the payment provider.
     */
    @Transactional
    Order reserveAndPersist(CreateOrderRequest request, PaymentProvider provider, String ownerRef) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyerEmail(request.getBuyerEmail())
                .externalRef(ownerRef)
                .event(event)
                .status(OrderStatus.AWAITING_PAYMENT)
                .providerId(provider.id())
                .referenceCode(generateReferenceCode())
                .currency("CAD")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(provider.holdDuration()))
                .items(new ArrayList<>())
                .build();

        // Sort by ticket type UUID to guarantee a consistent lock acquisition order
        // across concurrent transactions and prevent deadlocks.
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getTicketTypeId))
                .toList();

        BigDecimal amountTotal = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : sortedItems) {
            TicketType ticketType = ticketTypeRepository.findById(itemReq.getTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "TicketType not found: " + itemReq.getTicketTypeId()));
            if (ticketType.getEvent() == null || !ticketType.getEvent().getId().equals(event.getId())) {
                throw new IllegalArgumentException(
                        "Ticket type " + ticketType.getId() + " does not belong to event " + event.getId());
            }

            // Reserve one seat; throws ConflictException if sold out (rolls back the whole
            // order).
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
        return orderRepository.save(order);
    }

    /**
     * Phase 3: record the provider reference returned by {@code initiate} and — for
     * providers that
     * settle synchronously ({@link PaymentInitiation.Completed}) — confirm payment
     * immediately. Runs
     * in its own transaction, after the provider call, taking a fresh row lock on
     * the order.
     */
    @Transactional
    Order finalizeInitiation(UUID orderId, PaymentInitiation initiation) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        order.setProviderRef(initiation.providerRef());
        order = orderRepository.save(order);

        if (initiation instanceof PaymentInitiation.Completed) {
            return confirmationService.confirmPayment(orderId, initiation.providerRef());
        }
        return order;
    }

    /**
     * Failure path for {@link OrderService#createOrder}: if the provider could not
     * begin payment,
     * release the just-reserved hold and cancel the order so seats aren't stranded
     * until the expiry
     * sweep. Idempotent against an order that has already moved on.
     */
    @Transactional
    void releaseHold(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return; // already cancelled/expired/paid elsewhere — nothing to release
        }
        inventoryService.releaseAll(InventoryService.seatsByTicketType(order.getItems()));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    /**
     * Expires a single overdue order in its own transaction: re-load under the order-row lock,
     * re-check it is still {@code AWAITING_PAYMENT} (a buyer-cancel or late confirmation may have
     * moved it since the sweep's unlocked candidate query), then release its hold.
     *
     * <p>One transaction per order is deliberate. A single sweep-wide transaction would accumulate
     * order-row and ticket-type locks across candidates in an order that violates the global
     * "order row, then ticket-type rows" acquisition order, deadlocking against concurrent
     * confirm/cancel transactions — and one failed candidate would roll back every other expiry.
     *
     * @return {@code true} if this call expired the order; {@code false} if it had already moved on
     */
    @Transactional
    boolean expireIfStillAwaiting(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return false;
        }
        inventoryService.releaseAll(InventoryService.seatsByTicketType(order.getItems()));
        order.setStatus(OrderStatus.EXPIRED);
        orderRepository.save(order);
        return true;
    }

    private String generateRaw() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * TODO: Decide if this is sufficient for ETransfers
     * @return
     */
    private String generateReferenceCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = formatCode(generateRaw());
            if (!orderRepository.existsByReferenceCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate a unique reference code after 10 attempts");
    }

    public static String formatCode(String raw) {
        return raw.substring(0, 4) + "-" + raw.substring(4);
    }
}
