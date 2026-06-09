package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpiryServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @InjectMocks
    private OrderExpiryService expiryService;

    @Test
    void sweep_expiresAndReleasesInventory() {
        Order o = Order.builder().id(UUID.randomUUID()).referenceCode("ORD-1")
                .status(OrderStatus.AWAITING_PAYMENT).build();
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(o));
        when(orderRepository.findByIdForUpdate(o.getId())).thenReturn(Optional.of(o));

        expiryService.sweepExpiredOrders();

        assertEquals(OrderStatus.EXPIRED, o.getStatus());
        verify(orderService, times(1)).releaseInventory(o);
        verify(orderRepository, times(1)).save(o);
    }

    @Test
    void sweep_orderMovedOnBeforeLock_isSkipped() {
        UUID id = UUID.randomUUID();
        Order stale = Order.builder().id(id).referenceCode("ORD-2")
                .status(OrderStatus.AWAITING_PAYMENT).build();
        // Between the unlocked query and the locked re-read, a buyer-cancel won the row.
        Order locked = Order.builder().id(id).referenceCode("ORD-2")
                .status(OrderStatus.CANCELLED).build();
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.of(locked));

        expiryService.sweepExpiredOrders();

        verify(orderService, never()).releaseInventory(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void sweep_noExpiredOrders_noop() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of());

        expiryService.sweepExpiredOrders();

        verify(orderService, never()).releaseInventory(any());
        verify(orderRepository, never()).save(any());
    }
}
