package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.OrderItemRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private PaymentProvider provider;

    @InjectMocks
    private OrderService orderService;

    private Event event;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                .id(UUID.randomUUID()).name("Gala").time(LocalDateTime.now())
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
        when(orderTransactions.reserveAndPersist(any(), eq(provider))).thenReturn(pending);
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
        when(orderTransactions.reserveAndPersist(any(), eq(provider))).thenReturn(pending);
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
    void createOrder_providerInitiateFails_releasesHoldAndRethrows() {
        Order pending = pendingOrder();
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(orderTransactions.reserveAndPersist(any(), eq(provider))).thenReturn(pending);
        when(provider.initiate(any())).thenThrow(new RuntimeException("provider unavailable"));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(request()));

        verify(orderTransactions, times(1)).releaseHold(pending.getId());
        verify(orderTransactions, never()).finalizeInitiation(any(), any());
    }

    @Test
    void cancelOrder_awaiting_releasesInventory() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryService, times(1)).release(ticketType.getId(), 1);
    }

    @Test
    void cancelOrder_paid_throwsConflict() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
    }
}
