package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.OrderItemRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.order.OrderNotification;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProviderRegistry;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Tests the {@code createOrder} orchestration — that the inventory hold, provider call, and
 * finalization are sequenced through {@link OrderTransactions} (so no DB lock is held across the
 * provider call), plus the cancellation path. The reserve/persist/finalize transaction bodies
 * themselves are covered by {@link OrderTransactionsTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private EventRepository eventRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentProviderRegistry providerRegistry;
    @Mock private OrderTransactions orderTransactions;
    @Mock private OrderOwnerResolver ownerResolver;
    @Mock private OrderProperties orderProperties;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PaymentProvider provider;

    @InjectMocks
    private OrderService orderService;

    private Event event;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                .id(UUID.randomUUID()).name("Gala").time(OffsetDateTime.now())
                .location("Hall").description("D").build();
        ticketType = TicketType.builder()
                .id(UUID.randomUUID()).event(event).name("GA").price(new BigDecimal("10.00")).build();
    }

    private CreateOrderRequest request() {
        OrderItemRequest item = new OrderItemRequest(ticketType.getId(), "Jane", "Doe", "jane@example.com");
        return new CreateOrderRequest("buyer@example.com", event.getId(), null, List.of(item));
    }

    private Order pendingOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .event(event)
                .status(OrderStatus.AWAITING_PAYMENT)
                .amountTotal(new BigDecimal("10.00"))
                .currency("CAD")
                .referenceCode("ORD-TEST")
                .buyerEmail("buyer@example.com")
                .build();
    }

    @Test
    void createOrder_manualProvider_recordsInitiationRef() {
        Order pending = pendingOrder();
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), any())).thenReturn(pending);
        PaymentInitiation initiation = new PaymentInitiation.Instructions("pref", "pay please", Map.of());
        when(provider.initiate(any())).thenReturn(initiation);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), eq(initiation)))
                .thenAnswer(inv -> { pending.setProviderRef("pref"); return pending; });

        OrderCreationResult result = orderService.createOrder(request());

        assertEquals(OrderStatus.AWAITING_PAYMENT, result.order().getStatus());
        assertEquals("pref", result.order().getProviderRef());
        assertInstanceOf(PaymentInitiation.Instructions.class, result.initiation());
        verify(orderTransactions, never()).releaseHold(any());
    }

    @Test
    void createOrder_autoConfirmProvider_confirmsImmediately() {
        Order pending = pendingOrder();
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), any())).thenReturn(pending);
        PaymentInitiation initiation = new PaymentInitiation.Completed("pref");
        when(provider.initiate(any())).thenReturn(initiation);
        Order paid = pendingOrder();
        paid.setStatus(OrderStatus.PAID);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), eq(initiation))).thenReturn(paid);

        OrderCreationResult result = orderService.createOrder(request());

        assertEquals(OrderStatus.PAID, result.order().getStatus());
        verify(orderTransactions, times(1)).finalizeInitiation(pending.getId(), initiation);
    }

    @Test
    void createOrder_freeOrder_settlesWithoutContactingProvider() {
        Order pending = pendingOrder();
        // Scale matters: summing 0.00-priced tickets gives "0.00", which is not equal() to ZERO.
        pending.setAmountTotal(new BigDecimal("0.00"));
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), any())).thenReturn(pending);
        Order paid = pendingOrder();
        paid.setStatus(OrderStatus.PAID);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), any())).thenReturn(paid);

        OrderCreationResult result = orderService.createOrder(request());

        assertEquals(OrderStatus.PAID, result.order().getStatus());
        assertInstanceOf(PaymentInitiation.Completed.class, result.initiation());
        assertEquals("free-ORD-TEST", result.initiation().providerRef());
        verify(provider, never()).initiate(any());
        verify(orderTransactions, never()).releaseHold(any());
    }

    @Test
    void createOrder_unpricedOrder_stillGoesThroughProvider() {
        Order pending = pendingOrder();
        // A missing total means the price is unknown, not free — it must not auto-approve.
        pending.setAmountTotal(null);
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), any())).thenReturn(pending);
        PaymentInitiation initiation = new PaymentInitiation.Instructions("pref", "pay please", Map.of());
        when(provider.initiate(any())).thenReturn(initiation);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), eq(initiation))).thenReturn(pending);

        orderService.createOrder(request());

        verify(provider, times(1)).initiate(any());
    }

    @Test
    void createOrder_providerInitiateFails_releasesHoldAndRethrows() {
        Order pending = pendingOrder();
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), any())).thenReturn(pending);
        when(provider.initiate(any())).thenThrow(new RuntimeException("provider unavailable"));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(request()));

        verify(orderTransactions, times(1)).releaseHold(pending.getId());
        verify(orderTransactions, never()).finalizeInitiation(any(), any());
    }

    @Test
    void createOrder_stampsResolvedOwnerRef() {
        Order pending = pendingOrder();
        when(ownerResolver.resolveOwnerRef(any())).thenReturn("lensbridge:user:42");
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), eq("lensbridge:user:42")))
                .thenReturn(pending);
        PaymentInitiation initiation = new PaymentInitiation.Instructions("pref", "pay", Map.of());
        when(provider.initiate(any())).thenReturn(initiation);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), eq(initiation))).thenReturn(pending);

        orderService.createOrder(request());

        verify(orderTransactions).reserveAndPersist(any(), eq(provider), eq("lensbridge:user:42"));
    }

    @Test
    void createOrder_guest_passesNullOwnerRef() {
        Order pending = pendingOrder();
        // ownerResolver returns null by default (no host resolver) -> guest order
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider), isNull())).thenReturn(pending);
        PaymentInitiation initiation = new PaymentInitiation.Instructions("pref", "pay", Map.of());
        when(provider.initiate(any())).thenReturn(initiation);
        when(orderTransactions.finalizeInitiation(eq(pending.getId()), eq(initiation))).thenReturn(pending);

        orderService.createOrder(request());

        verify(orderTransactions).reserveAndPersist(any(), eq(provider), isNull());
    }

    @Test
    void createOrder_requireOwnerWithoutRef_throwsBeforeReserving() {
        when(orderProperties.isRequireOwner()).thenReturn(true);
        // ownerResolver returns null by default -> no identified owner

        assertThrows(SecurityException.class, () -> orderService.createOrder(request()));

        verify(providerRegistry, never()).resolve(any());
        verify(orderTransactions, never()).reserveAndPersist(any(), any(), any());
    }

    @Test
    void getOrdersByExternalRef_delegatesToRepository() {
        Order o = pendingOrder();
        when(orderRepository.findByExternalRef("ref-1")).thenReturn(List.of(o));

        List<Order> result = orderService.getOrdersByExternalRef("ref-1");

        assertEquals(1, result.size());
        assertSame(o, result.get(0));
    }

    @Test
    void findByReferenceCode_normalizesWhatTheOperatorTyped() {
        // The code is being retyped or pasted off the order book, so case and stray spacing are the
        // operator's, not the data's.
        Order o = pendingOrder();
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        assertSame(o, orderService.findByReferenceCode("  abcd-efgh  ").orElseThrow());
    }

    @Test
    void findByReferenceCode_missIsEmptyNotAnError() {
        // A wrong code is the operator being told they mistyped it, not an exceptional condition.
        when(orderRepository.findByReferenceCode("ZZZZ-ZZZZ")).thenReturn(Optional.empty());

        assertTrue(orderService.findByReferenceCode("ZZZZ-ZZZZ").isEmpty());
    }

    @Test
    void findByReferenceCode_blankIsEmptyWithoutQuerying() {
        assertTrue(orderService.findByReferenceCode(null).isEmpty());
        assertTrue(orderService.findByReferenceCode("   ").isEmpty());
        verify(orderRepository, never()).findByReferenceCode(any());
    }

    @Test
    void cancelOrder_awaiting_releasesInventory() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryService, times(1)).releaseAll(Map.of(ticketType.getId(), 1));
        verify(eventPublisher, times(1)).publishEvent(
                new OrderNotificationEvent(order.getId(), OrderNotification.CANCELLED));
    }

    @Test
    void cancelOrder_paid_throwsConflict() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
        verifyNoInteractions(eventPublisher);
    }
}
