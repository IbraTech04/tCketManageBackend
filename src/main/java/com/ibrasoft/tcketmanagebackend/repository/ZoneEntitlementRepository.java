package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ZoneEntitlementRepository extends JpaRepository<ZoneEntitlement, UUID> {

    Optional<ZoneEntitlement> findByTicketType_IdAndZone_Id(UUID ticketTypeId, UUID zoneId);

    List<ZoneEntitlement> findByTicketType_Id(UUID ticketTypeId);

    List<ZoneEntitlement> findByZone_Id(UUID zoneId);
}
