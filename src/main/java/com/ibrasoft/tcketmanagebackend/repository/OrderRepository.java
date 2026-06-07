package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByReferenceCode(String referenceCode);

    Optional<Order> findByProviderId(String providerId);

    Optional<Order> findByProviderRef(String providerRef);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime cutoff);

    List<Order> findByEventId(UUID eventId);
}
