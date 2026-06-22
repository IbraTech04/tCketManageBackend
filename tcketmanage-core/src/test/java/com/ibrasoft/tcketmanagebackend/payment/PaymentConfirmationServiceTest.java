package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FulfillmentService fulfillmentService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentConfirmationService confirmationService;

    private Order order(OrderStatus status) {
        return Order.builder().id(UUID.randomUUID()).status(status).build();
    }

    private Order orderWithSeat(OrderStatus status, TicketType ticketType) {
        return Order.builder().id(UUID.randomUUID()).status(status)
                .items(List.of(OrderItem.builder().ticketType(ticketType).build())).build();
    }

    @Test
    void confirm_awaitingPayment_fulfillsAndMarksPaid() {
        Order o = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-1");

        assertEquals(OrderStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        assertEquals("ref-1", result.getProviderRef());
        verify(fulfillmentService, times(1)).fulfill(o);
    }

    @Test
    void confirm_alreadyPaid_isIdempotentNoOp() {
        Order o = order(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-1");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(fulfillmentService, never()).fulfill(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_refundPending_isIdempotentNoOp() {
        Order o = order(OrderStatus.REFUND_PENDING);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-1");

        assertEquals(OrderStatus.REFUND_PENDING, result.getStatus());
        verify(fulfillmentService, never()).fulfill(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_expiredButSeatsAvailable_reReservesAndFulfills() {
        TicketType ticketType = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Order o = orderWithSeat(OrderStatus.EXPIRED, ticketType);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(inventoryService.tryReserveAll(Map.of(ticketType.getId(), 1))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-late");

        assertEquals(OrderStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        verify(fulfillmentService, times(1)).fulfill(o);
    }

    @Test
    void confirm_expiredButSoldOut_marksRefundPending() {
        TicketType ticketType = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Order o = orderWithSeat(OrderStatus.EXPIRED, ticketType);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(inventoryService.tryReserveAll(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-late");

        assertEquals(OrderStatus.REFUND_PENDING, result.getStatus());
        assertEquals("ref-late", result.getProviderRef());
        assertNull(result.getPaidAt());
        verify(fulfillmentService, never()).fulfill(any());
    }

    @Test
    void confirm_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> confirmationService.confirmPayment(id, null));
    }

    @Test
    void quarantine_awaitingPayment_marksQuarantined() {
        Order o = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.quarantineOrder(o.getId());

        assertEquals(OrderStatus.QUARANTINED, result.getStatus());
        verify(fulfillmentService, never()).fulfill(any());
    }

    @Test
    void quarantine_alreadyPaid_isNoOp() {
        Order o = order(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.quarantineOrder(o.getId());

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void quarantine_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> confirmationService.quarantineOrder(id));
    }

    @Test
    void quarantine_publishesEvent() {
        Order o = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        confirmationService.quarantineOrder(o.getId());

        verify(eventPublisher, times(1)).publishEvent(any(OrderQuarantinedEvent.class));
    }

    // --- approveQuarantine -------------------------------------------------------------------

    @Test
    void approve_quarantined_fulfillsAndMarksPaidWithoutReReserving() {
        Order o = order(OrderStatus.QUARANTINED);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.approveQuarantine(o.getId());

        assertEquals(OrderStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        verify(fulfillmentService, times(1)).fulfill(o);
        // Seats were held through the quarantine — no re-reservation should happen.
        verify(inventoryService, never()).tryReserveAll(any());
    }

    @Test
    void approve_alreadyPaid_isIdempotentNoOp() {
        Order o = order(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.approveQuarantine(o.getId());

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(fulfillmentService, never()).fulfill(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void approve_notQuarantined_throws() {
        Order o = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        assertThrows(ConflictException.class, () -> confirmationService.approveQuarantine(o.getId()));
        verify(fulfillmentService, never()).fulfill(any());
    }

    // --- denyQuarantine ----------------------------------------------------------------------

    @Test
    void deny_noFunds_releasesSeatsAndCancels() {
        TicketType ticketType = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Order o = orderWithSeat(OrderStatus.QUARANTINED, ticketType);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.denyQuarantine(o.getId(), false);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryService, times(1)).releaseAll(Map.of(ticketType.getId(), 1));
    }

    @Test
    void deny_fundsReceived_releasesSeatsAndMarksRefundPending() {
        TicketType ticketType = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Order o = orderWithSeat(OrderStatus.QUARANTINED, ticketType);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = confirmationService.denyQuarantine(o.getId(), true);

        assertEquals(OrderStatus.REFUND_PENDING, result.getStatus());
        verify(inventoryService, times(1)).releaseAll(Map.of(ticketType.getId(), 1));
    }

    @Test
    void deny_alreadyCancelled_isIdempotentNoOp() {
        Order o = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.denyQuarantine(o.getId(), false);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryService, never()).releaseAll(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deny_notQuarantined_throws() {
        Order o = order(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        assertThrows(ConflictException.class, () -> confirmationService.denyQuarantine(o.getId(), false));
        verify(inventoryService, never()).releaseAll(any());
    }
}
