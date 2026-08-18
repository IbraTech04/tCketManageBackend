package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The transactional half of order-notification delivery, kept separate from
 * {@link EmailDispatchService} for the same reason as {@link TicketEmailSender}: a
 * {@code @Transactional} method self-invoked on the async bean would be bypassed by the proxy.
 *
 * <p>{@code Order.items} is a lazy {@code @OneToMany}, so a plain {@code findById} would hand the
 * caller a detached entity that throws {@code LazyInitializationException} the moment the template
 * iterates the line items. {@link OrderRepository#findByIdWithItems} fetch-joins them inside this
 * short read transaction instead; {@code event} and each item's {@code ticketType} are eager
 * {@code @ManyToOne}s and come along with it. The SMTP send then happens with no transaction open.
 */
@Service
@AllArgsConstructor
public class OrderEmailSender {

    private final OrderRepository orderRepository;

    /** Loads an order with its items initialized, or empty if it no longer exists. Detached. */
    @Transactional(readOnly = true)
    public Optional<Order> load(UUID orderId) {
        return orderRepository.findByIdWithItems(orderId);
    }
}
