package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderResponseTest {

    private Order.OrderBuilder baseOrder() {
        Event event = Event.builder().id(UUID.randomUUID()).name("Gala")
                .time(LocalDateTime.now()).location("Hall").description("D").build();
        return Order.builder()
                .id(UUID.randomUUID())
                .buyerEmail("buyer@example.com")
                .event(event)
                .status(OrderStatus.AWAITING_PAYMENT)
                .referenceCode("ORD-TEST");
    }

    @Test
    void from_carriesExternalRef() {
        Order order = baseOrder().externalRef("lensbridge:user:42").build();

        assertEquals("lensbridge:user:42", OrderResponse.from(order).getExternalRef());
    }

    @Test
    void from_guestOrder_externalRefNull() {
        Order order = baseOrder().build();

        assertNull(OrderResponse.from(order).getExternalRef());
    }
}
