package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScanEventRepository extends JpaRepository<ScanEvent, UUID> {

    /**
     * Count how many times a specific ticket has scanned into a specific zone.
     */
    @Query("SELECT COUNT(se) FROM ScanEvent se " +
            "WHERE se.ticketId = :ticketId AND se.zone.id = :zoneId")
    int countZoneEntriesByTicketId(UUID ticketId, UUID zoneId);

    List<ScanEvent> findByTicketId(UUID ticketId);

    Page<ScanEvent> findByZone_Id(UUID zoneId, Pageable pageable);

    Page<ScanEvent> findByZone_IdIn(List<UUID> zoneIds, Pageable pageable);
}
