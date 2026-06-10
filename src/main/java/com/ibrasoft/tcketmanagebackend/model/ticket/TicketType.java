package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code @DynamicUpdate} so flushes only write the columns a transaction actually changed:
 * {@code reservedCount} is maintained by atomic conditional UPDATEs in {@code InventoryService},
 * and a full-row UPDATE from an unrelated edit must not overwrite it with a stale value.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@DynamicUpdate
@Table(name = "ticket_types")
public class TicketType {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /**
     * The event this ticket type belongs to. Ticket types are event-scoped.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnore
    private Event event;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    @NotNull
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Maximum number of seats that may be sold for this ticket type. {@code null} means unlimited.
     */
    private Integer capacity;

    /**
     * Live count of seats consumed (held by pending orders + sold). Available = capacity - reservedCount.
     */
    @Column(name = "reserved_count", nullable = false)
    @Builder.Default
    private Integer reservedCount = 0;

    /**
     * Per-zone access grants and entry limits. The presence of an entitlement grants access
     * to the referenced zone; its {@code maxEntries} caps how many times the ticket may enter.
     */
    @OneToMany(mappedBy = "ticketType", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZoneEntitlement> entitlements = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
