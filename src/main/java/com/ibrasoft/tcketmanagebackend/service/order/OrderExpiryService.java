package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically expires unpaid orders past their hold window and releases the inventory they held.
 */
@Service
@AllArgsConstructor
public class OrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${payments.sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpiredOrders() {
        List<Order> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.AWAITING_PAYMENT, LocalDateTime.now());
        for (Order order : expired) {
            orderService.releaseInventory(order);
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            log.info("Expired order {} ({}) and released its reserved inventory",
                    order.getId(), order.getReferenceCode());
        }
    }
}
