package com.ibrasoft.tcketmanagebackend.model.ticket.event;

import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single ticket scan into a zone. Used as:
 * A) an audit log of scan attempts, and
 * B) the basis for counting how many times a ticket has entered a zone.
 *
 * Uses a surrogate UUID primary key with an index on (ticket_id, zone_id). The previous
 * design keyed on (ticketId, timestamp-in-millis), which collided when a ticket was scanned
 * twice within the same millisecond.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "tcket:scan_events",
       indexes = @Index(name = "idx_scan_ticket_zone", columnList = "ticket_id, zone_id"))
public class ScanEvent {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private Instant timestamp;
}
