package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository <Ticket, UUID> {
    @Query("SELECT t FROM Ticket t WHERE t.event.id = :event")
    Page<Ticket> findByEvent(UUID event, Pageable pageable);

    /** All tickets for an event — used by the "resend all" delivery flow. */
    List<Ticket> findByEvent_Id(UUID eventId);

    /** Tickets for an event that have never been successfully emailed — the "send missing" flow. */
    List<Ticket> findByEvent_IdAndLastTicketSentIsNull(UUID eventId);

    /** Ticket ids for an event — the async "resend all" job only needs ids to dispatch by. */
    @Query("SELECT t.ID FROM Ticket t WHERE t.event.id = :eventId")
    List<UUID> findIdsByEvent_Id(UUID eventId);

    /** Ids of an event's never-sent tickets — the async "send missing" job. */
    @Query("SELECT t.ID FROM Ticket t WHERE t.event.id = :eventId AND t.lastTicketSent IS NULL")
    List<UUID> findIdsByEvent_IdAndLastTicketSentIsNull(UUID eventId);

    /** Pessimistic Write used when looking up tickets at scan-time */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.ID = :id")
    Optional<Ticket> findByIdForUpdate(UUID id);
}
