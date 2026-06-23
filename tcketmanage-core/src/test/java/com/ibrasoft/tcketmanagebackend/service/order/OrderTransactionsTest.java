package com.ibrasoft.tcketmanagebackend.service.order;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTransactionsTest {

    @Mock private OrderRepository orderRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentConfirmationService confirmationService;
    @Mock private PaymentProvider provider;

    @InjectMocks
    private OrderTransactions orderTransactions;

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

    @Test
    void reserveAndPersist_reservesSeatAndTotalsPrice() {
        when(provider.id()).thenReturn("mock");
        when(provider.holdDuration()).thenReturn(Duration.ofMinutes(30));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderTransactions.reserveAndPersist(request(), provider, "owner-1");

        assertEquals(OrderStatus.AWAITING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("10.00"), order.getAmountTotal());
        assertEquals("mock", order.getProviderId());
        assertEquals("owner-1", order.getExternalRef());
        assertNotNull(order.getExpiresAt());
        verify(inventoryService, times(1)).reserve(ticketType.getId(), 1);
    }

    @Test
    void reserveAndPersist_ticketTypeFromOtherEvent_throws() {
        Event other = Event.builder().id(UUID.randomUUID()).name("Other").time(OffsetDateTime.now())
                .location("X").description("Y").build();
        TicketType foreign = TicketType.builder().id(UUID.randomUUID()).event(other).name("GA")
                .price(BigDecimal.ONE).build();
        when(provider.id()).thenReturn("mock");
        when(provider.holdDuration()).thenReturn(Duration.ofMinutes(30));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        CreateOrderRequest req = new CreateOrderRequest("buyer@example.com", event.getId(), null,
                List.of(new OrderItemRequest(foreign.getId(), "A", "B", "a@b.com")));

        assertThrows(IllegalArgumentException.class, () -> orderTransactions.reserveAndPersist(req, provider, null));
        verify(inventoryService, never()).reserve(any(), anyInt());
    }

    @Test
    void finalizeInitiation_completed_confirmsPayment() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        Order paid = Order.builder().id(o.getId()).status(OrderStatus.PAID).build();
        when(confirmationService.confirmPayment(o.getId(), "pref")).thenReturn(paid);

        Order result = orderTransactions.finalizeInitiation(o.getId(), new PaymentInitiation.Completed("pref"));

        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals("pref", o.getProviderRef());
        verify(confirmationService, times(1)).confirmPayment(o.getId(), "pref");
    }

    @Test
    void finalizeInitiation_instructions_recordsRefWithoutConfirming() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderTransactions.finalizeInitiation(
                o.getId(), new PaymentInitiation.Instructions("pref", "pay", Map.of()));

        assertEquals("pref", result.getProviderRef());
        assertEquals(OrderStatus.AWAITING_PAYMENT, result.getStatus());
        verify(confirmationService, never()).confirmPayment(any(), any());
    }

    @Test
    void releaseHold_awaiting_releasesInventoryAndCancels() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderTransactions.releaseHold(o.getId());

        assertEquals(OrderStatus.CANCELLED, o.getStatus());
        verify(inventoryService, times(1)).releaseAll(Map.of(ticketType.getId(), 1));
    }

    @Test
    void releaseHold_alreadyMovedOn_isNoOp() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        orderTransactions.releaseHold(o.getId());

        verify(inventoryService, never()).releaseAll(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void expireIfStillAwaiting_awaiting_releasesInventoryAndExpires() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(orderTransactions.expireIfStillAwaiting(o.getId()));

        assertEquals(OrderStatus.EXPIRED, o.getStatus());
        verify(inventoryService, times(1)).releaseAll(Map.of(ticketType.getId(), 1));
    }

    @Test
    void expireIfStillAwaiting_orderMovedOnUnderTheLock_isSkipped() {
        // Between the sweep's unlocked candidate query and this locked re-read, a buyer-cancel
        // (or a late confirmation) won the row — the hold must not be released twice.
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.CANCELLED)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        assertFalse(orderTransactions.expireIfStillAwaiting(o.getId()));

        verify(inventoryService, never()).releaseAll(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void expireIfStillAwaiting_orderGone_isSkipped() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertFalse(orderTransactions.expireIfStillAwaiting(id));

        verify(inventoryService, never()).releaseAll(any());
    }
}
