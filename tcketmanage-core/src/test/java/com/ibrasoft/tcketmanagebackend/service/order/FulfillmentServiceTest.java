package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderItem;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FulfillmentService fulfillmentService;

    private Order orderWithExternalRef(String externalRef) {
        Event event = Event.builder().id(UUID.randomUUID()).name("Gala")
                .time(LocalDateTime.now()).location("Hall").description("D").build();
        TicketType ticketType = TicketType.builder()
                .id(UUID.randomUUID()).event(event).name("GA").price(new BigDecimal("10.00")).build();
        OrderItem item = OrderItem.builder()
                .ticketType(ticketType)
                .attendeeFirstName("Jane").attendeeLastName("Doe").attendeeEmail("jane@example.com")
                .unitPrice(ticketType.getPrice())
                .build();
        return Order.builder()
                .id(UUID.randomUUID())
                .externalRef(externalRef)
                .event(event)
                .items(List.of(item))
                .build();
    }

    @Test
    void fulfill_defaultsHolderRefToOrderExternalRef() {
        Order order = orderWithExternalRef("lensbridge:user:7");

        fulfillmentService.fulfill(order);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        Ticket issued = captor.getValue();
        assertEquals("lensbridge:user:7", issued.getHolderRef());
        assertEquals(TicketStatus.ACTIVE, issued.getStatus());
        verify(eventPublisher, times(1)).publishEvent(any(TicketsIssuedEvent.class));
    }

    @Test
    void fulfill_guestOrder_holderRefNull() {
        Order order = orderWithExternalRef(null);

        fulfillmentService.fulfill(order);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        assertNull(captor.getValue().getHolderRef());
    }
}
