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

    List<Ticket> findByEvent_Id(UUID eventId);

    List<Ticket> findByOrder_Id(UUID orderId);

    List<Ticket> findByEvent_IdAndLastTicketSentIsNull(UUID eventId);

    @Query("SELECT t.ID FROM Ticket t WHERE t.event.id = :eventId")
    List<UUID> findIdsByEvent_Id(UUID eventId);

    @Query("SELECT t.ID FROM Ticket t WHERE t.event.id = :eventId AND t.lastTicketSent IS NULL")
    List<UUID> findIdsByEvent_IdAndLastTicketSentIsNull(UUID eventId);

    /** Pessimistic Write used when looking up tickets at scan-time */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.ID = :id")
    Optional<Ticket> findByIdForUpdate(UUID id);
}
