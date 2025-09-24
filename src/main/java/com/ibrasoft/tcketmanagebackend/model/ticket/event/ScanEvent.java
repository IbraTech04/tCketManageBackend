package com.ibrasoft.tcketmanagebackend.model.ticket.event;

import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "scan_events")
/**
 * This class represents a scan event for tickets.
 * This class will be used as:
 * A) An audit log for ticket scans
 * B) An easy way to track the number of times someone has entered a zone
 *
 * This class will use a composite key of ticket ID and timestamp to ensure uniqueness.
 */
public class ScanEvent {
    @EmbeddedId
    private ScanEventId id;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    private Zone zone;
}
