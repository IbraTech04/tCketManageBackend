package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanEventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanOutcome;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ValidationResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.ZoneEntitlement;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneEntitlementRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Transactional
public class ScanEventService {

    private final ScanEventRepository scanEventRepository;
    private final TicketRepository ticketRepository;
    private final ZoneRepository zoneRepository;
    private final EventRepository eventRepository;
    private final ZoneEntitlementRepository entitlementRepository;
    private final CryptoService cryptoService;

    /**
     * Scans a ticket from its signed QR payload: decodes, verifies the signature, checks the token is
     * actually bound to the ticket's current event, then performs the normal zone scan. Rejects
     * malformed, tampered and mis-bound payloads before touching the entitlement tables.
     */
    public ScanResult scanByQr(String qrPayload, UUID zoneId) {
        TicketQRData data;
        try {
            data = cryptoService.verify(qrPayload);
        } catch (Exception e) {
            return new ScanResult(ScanOutcome.INVALID_QR, "Invalid or tampered QR code", null);
        }
        return scan(data.getTicketID(), zoneId, data.getEventID());
    }

    /**
     * Scans by ticket id — the operator/manual-entry path. No token is presented, so there is no
     * event binding to cross-check; the caller has already been authorized by
     * {@code @tcketmanageAuthz.canScan()}.
     */
    public ScanResult scanTicket(UUID ticketId, UUID zoneId) {
        return scan(ticketId, zoneId, null);
    }

    /**
     * The single scan implementation behind both entry points.
     *
     * @param tokenEventId the {@code eventID} carried by the signed QR payload, or {@code null} when
     *                     no token was presented (the manual {@link #scanTicket} path). Non-null
     *                     means the token's event binding must match the ticket's event.
     */
    private ScanResult scan(UUID ticketId, UUID zoneId, UUID tokenEventId) {
        Ticket ticket = requireTicket(ticketId);

        // SECURITY: the signed payload carries the event the token was minted for, and until now that
        // field was decoded and thrown away — so the signature bound a token to a ticket id and
        // nothing else, and the eventID in the payload asserted nothing. Comparing it to the ticket's
        // current event makes the binding real: a token stays valid only for the event its ticket
        // still belongs to, so a ticket re-pointed at a different event (import fix-up, event merge)
        // invalidates the tokens already in circulation for it instead of silently carrying them over.
        //
        // A mismatch is reported as INVALID_QR rather than a ticket-level denial because that is what
        // it is: the token, not the ticket, is wrong for what it is being presented against.
        //
        // Both sides must be present. CryptoService.sign refuses to mint a token whose eventID is
        // null, so any legitimately signed payload carries one; a ticket that has lost its event has
        // lost the thing the token was bound to. Rejected alternative: skipping the comparison when
        // either side is null — that converts the guard into a bypass triggered by exactly the
        // degenerate inputs it exists to catch.
        if (tokenEventId != null) {
            UUID ticketEventId = ticket.getEvent() != null ? ticket.getEvent().getId() : null;
            if (!tokenEventId.equals(ticketEventId)) {
                return new ScanResult(ScanOutcome.INVALID_QR,
                    "QR code was not issued for this ticket's event", null);
            }
        }

        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            return new ScanResult(ScanOutcome.NO_ZONE_ENTITLEMENT,
                String.format("Ticket status is %s, expected ACTIVE", ticket.getStatus()), null);
        }

        Zone zone = requireZone(zoneId);

        Optional<ZoneEntitlement> entitlement = findEntitlement(ticket, zoneId);
        if (entitlement.isEmpty()) {
            return new ScanResult(ScanOutcome.NO_ZONE_ENTITLEMENT, "Ticket does not have access to this zone", null);
        }

        Integer maxEntries = entitlement.get().getMaxEntries();
        int currentEntryCount = scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId);
        if (maxEntries != null && currentEntryCount >= maxEntries) {
            return new ScanResult(ScanOutcome.ENTRY_LIMIT_REACHED,
                String.format("Ticket has reached its entry limit for this zone (%d/%d)",
                    currentEntryCount, maxEntries), null);
        }

        ScanEvent saved = scanEventRepository.save(ScanEvent.builder()
                .ticketId(ticketId)
                .zone(zone)
                .timestamp(Instant.now())
                .build());

        String entryLabel = maxEntries == null
            ? String.valueOf(currentEntryCount + 1)
            : String.format("%d/%d", currentEntryCount + 1, maxEntries);
        return new ScanResult(ScanOutcome.SUCCESS,
            String.format("Scan successful. Entry %s for zone %s", entryLabel, zone.getName()),
            ScanEventResponse.from(saved));
    }

    /**
     * Answers "may this ticket enter this zone?" without recording an entry. This is the endpoint a
     * turnstile or a door-side display is built on, so it must apply the same admission rules
     * {@link #scanTicket} applies — see the status guard below.
     */
    @Transactional(readOnly = true)
    public ValidationResult validateTicketForZone(UUID ticketId, UUID zoneId) {
        // Plain read, no lock: validation is advisory, and a PESSIMISTIC_WRITE inside a read-only
        // transaction is rejected outright by PostgreSQL. Only scanTicket needs the ticket-row lock.
        Ticket ticket = requireTicketReadOnly(ticketId);

        // SECURITY: mirror the status gate scanTicket applies. This endpoint exists precisely so a
        // gate can ask whether someone may enter *without* consuming an entry, which means it is the
        // check a turnstile actually runs — and it was answering purely on zone entitlement and entry
        // count. A CANCELLED or refunded ticket kept its entitlement rows, so it reported valid=true
        // and any gate trusting this endpoint admitted its holder, while the recording path next door
        // would have turned the same ticket away.
        //
        // Deliberately checked before requireZone so the answer matches scanTicket's ordering: a
        // ticket that is not ACTIVE is denied on its own merits, not on the zone it was presented at.
        //
        // Rejected alternative: leaving this to the caller. ValidationResult exposes only a boolean
        // and a human-readable message, so a caller cannot see the ticket's status to filter on it —
        // the check has to happen here or not at all.
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            return new ValidationResult(false,
                String.format("Ticket status is %s, expected ACTIVE", ticket.getStatus()));
        }

        requireZone(zoneId);

        Optional<ZoneEntitlement> entitlement = findEntitlement(ticket, zoneId);
        if (entitlement.isEmpty()) {
            return new ValidationResult(false, "Ticket does not have access to this zone");
        }

        Integer maxEntries = entitlement.get().getMaxEntries();
        int currentEntryCount = scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId);
        if (maxEntries != null && currentEntryCount >= maxEntries) {
            return new ValidationResult(false,
                String.format("Ticket has reached its entry limit (%d/%d)", currentEntryCount, maxEntries));
        }

        String remaining = maxEntries == null ? "unlimited" : String.valueOf(maxEntries - currentEntryCount);
        return new ValidationResult(true,
            String.format("Ticket valid. %s entries remaining", remaining));
    }

    @Transactional(readOnly = true)
    public List<ScanEvent> getScanHistoryForTicket(UUID ticketId) {
        return scanEventRepository.findByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public Page<ScanEvent> getScanHistoryForZone(UUID zoneId, Pageable pageable) {
        requireZone(zoneId);
        return scanEventRepository.findByZone_Id(zoneId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ScanEvent> getScanHistoryForEvent(UUID eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        List<UUID> zoneIds = event.getZones().stream()
                .map(Zone::getId)
                .collect(Collectors.toList());
        return scanEventRepository.findByZone_IdIn(zoneIds, pageable);
    }

    @Transactional(readOnly = true)
    public Integer getEntryCountForTicketAndZone(UUID ticketId, UUID zoneId) {
        return scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId);
    }

    /**
     * Looks up the entitlement granting this ticket's type access to the given zone, if any.
     */
    private Optional<ZoneEntitlement> findEntitlement(Ticket ticket, UUID zoneId) {
        if (ticket.getTicketType() == null) {
            return Optional.empty();
        }
        return entitlementRepository.findByTicketType_IdAndZone_Id(ticket.getTicketType().getId(), zoneId);
    }

    /**
     * Loads a ticket under the row lock that serializes concurrent scans (the count-then-insert
     * against the entry limit must not interleave).
     */
    private Ticket requireTicket(UUID ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private Ticket requireTicketReadOnly(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private Zone requireZone(UUID zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
    }
}
