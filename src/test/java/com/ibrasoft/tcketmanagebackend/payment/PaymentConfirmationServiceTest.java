package com.ibrasoft.tcketmanagebackend.payment;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import com.ibrasoft.tcketmanagebackend.service.order.FulfillmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private PaymentConfirmationService confirmationService;

    private Order order(OrderStatus status) {
        return Order.builder().id(UUID.randomUUID()).status(status).build();
    }

    @Test
    void confirm_awaitingPayment_fulfillsAndMarksPaid() {
        Order o = order(OrderStatus.AWAITING_PAYMENT);
        when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));
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
        when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));

        Order result = confirmationService.confirmPayment(o.getId(), "ref-1");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(fulfillmentService, never()).fulfill(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirm_cancelled_throwsConflict() {
        Order o = order(OrderStatus.CANCELLED);
        when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));

        assertThrows(ConflictException.class, () -> confirmationService.confirmPayment(o.getId(), null));
        verify(fulfillmentService, never()).fulfill(any());
    }

    @Test
    void confirm_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> confirmationService.confirmPayment(id, null));
    }
}
