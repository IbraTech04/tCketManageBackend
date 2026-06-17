package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Association entity linking a {@link TicketType} to a {@link Zone} it may enter,
 * carrying the per-zone entry limit.
 *
 * The presence of a row means "this ticket type may enter this zone"; {@code maxEntries}
 * is the total number of entries allowed (1 = single entry, null = unlimited). This replaces
 * the former bitmask + scalar reentry-limit model so limits can vary per zone.
 */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "zone_entitlements",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_zone_entitlement_type_zone",
           columnNames = {"ticket_type_id", "zone_id"}
       ))
public class ZoneEntitlement {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    @JsonIgnore
    private TicketType ticketType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "max_entries")
    @Min(1)
    private Integer maxEntries;
}
