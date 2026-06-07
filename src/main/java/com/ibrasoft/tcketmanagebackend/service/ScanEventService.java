package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanEventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ValidationResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
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

import java.time.LocalDateTime;
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
     * Scans a ticket from its signed QR payload: decodes, verifies the signature, then performs the
     * normal zone scan. Rejects malformed or tampered payloads before touching the database.
     */
    public ScanResult scanByQr(String qrPayload, UUID zoneId) {
        TicketQRData data;
        try {
            data = cryptoService.verify(qrPayload);
        } catch (Exception e) {
            return new ScanResult(false, "Invalid or tampered ticket", null);
        }
        return scanTicket(data.getTicketID(), zoneId);
    }

    public ScanResult scanTicket(UUID ticketId, UUID zoneId) {
        Ticket ticket = requireTicket(ticketId);
        Zone zone = requireZone(zoneId);

        Optional<ZoneEntitlement> entitlement = findEntitlement(ticket, zoneId);
        if (entitlement.isEmpty()) {
            return new ScanResult(false, "Ticket does not have access to this zone", null);
        }

        int maxEntries = entitlement.get().getMaxEntries();
        int currentEntryCount = scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId);
        if (currentEntryCount >= maxEntries) {
            return new ScanResult(false,
                String.format("Ticket has reached its entry limit for this zone (%d/%d)",
                    currentEntryCount, maxEntries), null);
        }

        ScanEvent saved = scanEventRepository.save(ScanEvent.builder()
                .ticketId(ticketId)
                .zone(zone)
                .timestamp(LocalDateTime.now())
                .build());

        return new ScanResult(true,
            String.format("Scan successful. Entry %d/%d for zone %s",
                currentEntryCount + 1, maxEntries, zone.getName()), ScanEventResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public ValidationResult validateTicketForZone(UUID ticketId, UUID zoneId) {
        Ticket ticket = requireTicket(ticketId);
        requireZone(zoneId);

        Optional<ZoneEntitlement> entitlement = findEntitlement(ticket, zoneId);
        if (entitlement.isEmpty()) {
            return new ValidationResult(false, "Ticket does not have access to this zone");
        }

        int maxEntries = entitlement.get().getMaxEntries();
        int currentEntryCount = scanEventRepository.countZoneEntriesByTicketId(ticketId, zoneId);
        if (currentEntryCount >= maxEntries) {
            return new ValidationResult(false,
                String.format("Ticket has reached its entry limit (%d/%d)", currentEntryCount, maxEntries));
        }

        return new ValidationResult(true,
            String.format("Ticket valid. Can enter %d more time(s)", maxEntries - currentEntryCount));
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

    private Ticket requireTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private Zone requireZone(UUID zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));
    }
}
