package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository <Ticket, UUID> {
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :event")
    Page<Ticket> findByEvent(UUID event, Pageable pageable);

    /** All tickets for an event — used by the "resend all" delivery flow. */
    List<Ticket> findByEvent_Id(UUID eventId);

    /** Tickets for an event that have never been successfully emailed — the "send missing" flow. */
    List<Ticket> findByEvent_IdAndLastTicketSentIsNull(UUID eventId);
}
