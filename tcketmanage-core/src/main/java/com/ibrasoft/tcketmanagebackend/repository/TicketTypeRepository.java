package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEvent_Id(UUID eventId);

    List<TicketType> findByEvent_IdAndIsActive(UUID eventId, Boolean isActive);

    /**
     * Loads a ticket type under a pessimistic write lock. Used by admin mutations so a concurrent
     * reserve/release can't be clobbered by the full-row UPDATE Hibernate emits on save.
     *
     * <p>CAUTION: if the entity is already in the persistence context, a locking query acquires the
     * DB lock but returns the already-managed instance with its <em>stale</em> state. Capacity
     * accounting therefore never goes through this method — it uses the atomic conditional updates
     * below, whose check and increment happen inside a single SQL statement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id")
    Optional<TicketType> findByIdForUpdate(UUID id);

    /**
     * Atomically reserves seats: the capacity check and the increment execute in one UPDATE, so no
     * interleaving (and no stale persistence-context state) can oversell. A {@code null} capacity
     * means unlimited. Returns the number of rows updated — 0 means the row is missing or capacity
     * is insufficient; the caller distinguishes the two.
     */
    @Modifying
    @Query("UPDATE TicketType tt SET tt.reservedCount = tt.reservedCount + :quantity "
            + "WHERE tt.id = :id AND (tt.capacity IS NULL OR tt.reservedCount + :quantity <= tt.capacity)")
    int reserveSeats(UUID id, int quantity);

    /**
     * Atomically releases seats, refusing to underflow: only applies when at least {@code quantity}
     * seats are currently reserved. Returns 0 if the row is missing or the decrement would go
     * negative (an accounting bug the caller should log before falling back to
     * {@link #releaseSeatsClamped}).
     */
    @Modifying
    @Query("UPDATE TicketType tt SET tt.reservedCount = tt.reservedCount - :quantity "
            + "WHERE tt.id = :id AND tt.reservedCount >= :quantity")
    int releaseSeats(UUID id, int quantity);

    /**
     * Fallback release that clamps at zero in the same atomic statement, so a concurrent reserve
     * landing between a failed {@link #releaseSeats} and this call is decremented correctly rather
     * than wiped to zero.
     */
    @Modifying
    @Query("UPDATE TicketType tt SET tt.reservedCount = "
            + "CASE WHEN tt.reservedCount >= :quantity THEN tt.reservedCount - :quantity ELSE 0 END "
            + "WHERE tt.id = :id")
    int releaseSeatsClamped(UUID id, int quantity);
}
