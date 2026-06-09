package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Loads an order under a pessimistic write lock so every state transition (confirm, cancel,
     * expire) serializes on the order row. This is what makes confirmation idempotent against
     * at-least-once webhook redelivery and prevents a buyer-cancel racing the expiry sweep into a
     * double inventory release.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    Optional<Order> findByReferenceCode(String referenceCode);

    Optional<Order> findByProviderId(String providerId);

    Optional<Order> findByProviderRef(String providerRef);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime cutoff);

    List<Order> findByEventId(UUID eventId);

    boolean existsByReferenceCode(String code);
}
