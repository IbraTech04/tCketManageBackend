package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
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
import com.ibrasoft.tcketmanagebackend.payment.PaymentProviderRegistry;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentProviderRegistry providerRegistry;
    @Mock private PaymentConfirmationService confirmationService;
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

    private void stubCommon(PaymentInitiation initiation) {
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(provider.id()).thenReturn("mock");
        when(provider.holdDuration()).thenReturn(Duration.ofMinutes(30));
        when(provider.initiate(any())).thenReturn(initiation);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createOrder_manualProvider_awaitingPayment() {
        stubCommon(new PaymentInitiation.Instructions("pref", "pay please", Map.of()));

        OrderCreationResult result = orderService.createOrder(request());

        assertEquals(OrderStatus.AWAITING_PAYMENT, result.order().getStatus());
        assertEquals(new BigDecimal("10.00"), result.order().getAmountTotal());
        assertEquals("pref", result.order().getProviderRef());
        assertInstanceOf(PaymentInitiation.Instructions.class, result.initiation());
        verify(inventoryService, times(1)).reserve(ticketType.getId(), 1);
        verify(confirmationService, never()).confirmPayment(any(), any());
    }

    @Test
    void createOrder_autoConfirmProvider_confirmsImmediately() {
        stubCommon(new PaymentInitiation.Completed("pref"));
        Order paid = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(confirmationService.confirmPayment(any(UUID.class), eq("pref"))).thenReturn(paid);

        OrderCreationResult result = orderService.createOrder(request());

        assertEquals(OrderStatus.PAID, result.order().getStatus());
        verify(confirmationService, times(1)).confirmPayment(any(UUID.class), eq("pref"));
    }

    @Test
    void createOrder_ticketTypeFromOtherEvent_throws() {
        Event other = Event.builder().id(UUID.randomUUID()).name("Other").time(LocalDateTime.now())
                .location("X").description("Y").build();
        TicketType foreign = TicketType.builder().id(UUID.randomUUID()).event(other).name("GA")
                .price(BigDecimal.ONE).build();
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(provider.id()).thenReturn("mock");
        when(provider.holdDuration()).thenReturn(Duration.ofMinutes(30));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        CreateOrderRequest req = new CreateOrderRequest("buyer@example.com", event.getId(), null,
                List.of(new OrderItemRequest(foreign.getId(), "A", "B", "a@b.com")));

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(req));
    }

    @Test
    void cancelOrder_awaiting_releasesInventory() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.cancelOrder(order.getId());

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryService, times(1)).release(ticketType.getId(), 1);
    }

    @Test
    void cancelOrder_paid_throwsConflict() {
        Order order = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> orderService.cancelOrder(order.getId()));
    }
}
