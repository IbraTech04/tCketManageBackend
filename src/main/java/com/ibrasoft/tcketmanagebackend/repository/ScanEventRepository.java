package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScanEventRepository extends JpaRepository<ScanEvent, ScanEventId> {
    /**
     * Count how many times a specific ticket has scanned into a specific zone by aggregating scan events
     */
    @Query("SELECT COUNT(se) FROM ScanEvent se " +
            "WHERE se.id.ticketId = :ticketId AND se.zone.id = :zoneId")
    public int countZoneEntriesByTicketId(UUID ticketId, UUID zoneId);
}
