package com.ibrasoft.tcketmanagebackend.model.ticket.event;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Composite key class for ScanEvent entity.
 */
@Data
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class ScanEventId implements Serializable {
    /**
     * The ID of the ticket that was scanned.
     */
    private UUID ticketId;

    /**
     * The timestamp of when the ticket was scanned.
     */
    private LocalDateTime timestamp;

    public ScanEventId(UUID ticketId, long millis){
        this(ticketId, LocalDateTime.ofEpochSecond(millis / 1000, (int)(millis % 1000) * 1_000_000, java.time.ZoneOffset.UTC));
    }
}
