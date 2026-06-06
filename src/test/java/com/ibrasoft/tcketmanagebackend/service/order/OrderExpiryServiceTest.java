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

        expiryService.sweepExpiredOrders();

        assertEquals(OrderStatus.EXPIRED, o.getStatus());
        verify(orderService, times(1)).releaseInventory(o);
        verify(orderRepository, times(1)).save(o);
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
