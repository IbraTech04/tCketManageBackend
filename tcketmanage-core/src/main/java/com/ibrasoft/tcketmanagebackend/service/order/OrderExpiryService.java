package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically expires unpaid orders past their hold window and releases the inventory they held.
 *
 * <p>The sweep itself is deliberately NOT transactional: each candidate is expired in its own
 * short transaction ({@link OrderTransactions#expireIfStillAwaiting}), so locks are never
 * accumulated across orders (which could deadlock against concurrent confirm/cancel transactions)
 * and one failing order doesn't roll back the rest of the sweep.
 */
@Service
@AllArgsConstructor
public class OrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);

    private final OrderRepository orderRepository;
    private final OrderTransactions orderTransactions;

    @Scheduled(fixedDelayString = "${payments.sweep-interval-ms:60000}")
    public void sweepExpiredOrders() {
        List<Order> candidates = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.AWAITING_PAYMENT, LocalDateTime.now());
        for (Order candidate : candidates) {
            try {
                if (orderTransactions.expireIfStillAwaiting(candidate.getId())) {
                    log.info("Expired order {} ({}) and released its reserved inventory",
                            candidate.getId(), candidate.getReferenceCode());
                }
            } catch (Exception e) {
                log.error("Failed to expire order {} ({}); will retry next sweep",
                        candidate.getId(), candidate.getReferenceCode(), e);
            }
        }
    }
}
