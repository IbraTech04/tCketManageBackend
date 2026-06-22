package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentProviderRegistry providerRegistry;
    @Mock private PaymentProvider provider;

    @InjectMocks
    private RefundService refundService;

    private Order paidOrder() {
        return Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).providerId("mock").build();
    }

    private Ticket ticket(TicketStatus status, TicketType type, Order order) {
        return Ticket.builder().ID(UUID.randomUUID()).firstName("A").lastName("B").email("a@b.com")
                .ticketType(type).status(status).order(order).build();
    }

    @Test
    void refund_autoProvider_voidsTicketsReleasesSeatsAndMarksRefunded() {
        Order o = paidOrder();
        TicketType type = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Ticket t1 = ticket(TicketStatus.ACTIVE, type, o);
        Ticket t2 = ticket(TicketStatus.ACTIVE, type, o);
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(ticketRepository.findByOrder_Id(o.getId())).thenReturn(List.of(t1, t2));
        when(providerRegistry.resolve("mock")).thenReturn(provider);
        when(provider.refund(o)).thenReturn(RefundOutcome.completed());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = refundService.refundOrder(o.getId());

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        assertEquals(TicketStatus.CANCELLED, t1.getStatus());
        assertEquals(TicketStatus.CANCELLED, t2.getStatus());
        verify(inventoryService, times(2)).release(type.getId(), 1);
    }

    @Test
    void refund_manualProvider_marksRefundPending() {
        Order o = paidOrder();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(ticketRepository.findByOrder_Id(o.getId())).thenReturn(List.of());
        when(providerRegistry.resolve("mock")).thenReturn(provider);
        when(provider.refund(o)).thenReturn(RefundOutcome.manualActionRequired("send it back"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = refundService.refundOrder(o.getId());

        assertEquals(OrderStatus.REFUND_PENDING, result.getStatus());
    }

    @Test
    void refund_skipsAlreadyVoidedTickets() {
        Order o = paidOrder();
        TicketType type = TicketType.builder().id(UUID.randomUUID()).name("GA").build();
        Ticket active = ticket(TicketStatus.ACTIVE, type, o);
        Ticket revoked = ticket(TicketStatus.REVOKED, type, o); // seat already released on revoke
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(ticketRepository.findByOrder_Id(o.getId())).thenReturn(List.of(active, revoked));
        when(providerRegistry.resolve("mock")).thenReturn(provider);
        when(provider.refund(o)).thenReturn(RefundOutcome.completed());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        refundService.refundOrder(o.getId());

        assertEquals(TicketStatus.REVOKED, revoked.getStatus());
        verify(inventoryService, times(1)).release(type.getId(), 1); // only the active ticket
    }

    @Test
    void refund_notPaid_throws() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.AWAITING_PAYMENT).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        assertThrows(ConflictException.class, () -> refundService.refundOrder(o.getId()));
        verify(inventoryService, never()).release(any(), anyInt());
    }

    @Test
    void refund_alreadyRefunded_isIdempotentNoOp() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.REFUNDED).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = refundService.refundOrder(o.getId());

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void refund_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> refundService.refundOrder(id));
    }

    @Test
    void markRefundComplete_refundPending_marksRefunded() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.REFUND_PENDING).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = refundService.markRefundComplete(o.getId());

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
    }

    @Test
    void markRefundComplete_alreadyRefunded_isNoOp() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.REFUNDED).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        Order result = refundService.markRefundComplete(o.getId());

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markRefundComplete_notPending_throws() {
        Order o = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PAID).build();
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        assertThrows(ConflictException.class, () -> refundService.markRefundComplete(o.getId()));
    }
}
