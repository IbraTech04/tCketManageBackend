package com.ibrasoft.tcketmanagebackend.model.order;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A purchase: one buyer acquiring one or more seats for a single event through a payment provider.
 * Inventory is held while {@code AWAITING_PAYMENT}; tickets are materialized on payment.
 */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "orders",
       uniqueConstraints = @UniqueConstraint(name = "uk_order_reference_code", columnNames = "reference_code"))
public class Order {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Email
    @NotBlank
    private String buyerEmail;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** Id of the payment provider handling this order (e.g. "mock", "stripe", "interac"). */
    @Column(nullable = false, length = 40)
    private String providerId;

    /** Provider-side reference (Stripe session id, etc.); null until {@code initiate}. */
    private String providerRef;

    /** Human-facing unique code, used as the Interac memo and for lookups. */
    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountTotal;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "CAD";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
