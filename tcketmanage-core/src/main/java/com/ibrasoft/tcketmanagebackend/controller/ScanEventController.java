package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.model.dto.request.QrScanRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ScanRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ValidateRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanEventResponse;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ScanResult;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ValidationResult;
import com.ibrasoft.tcketmanagebackend.service.ScanEventService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scanning, validation and scan-history reporting. Class-level {@code @PreAuthorize}: every endpoint
 * requires at least the configured scanner role (with the host's role hierarchy, the event-manager
 * and admin roles inherit it).
 */
@RestController
@RequestMapping("/api/v1/scans")
@AllArgsConstructor
@PreAuthorize("hasRole(@tcketmanageRoles.scanner)")
public class ScanEventController {

    private final ScanEventService scanEventService;

    @PostMapping("/scan")
    public ResponseEntity<ScanResult> scanTicket(@RequestBody ScanRequest request) {
        ScanResult result = scanEventService.scanTicket(request.getTicketId(), request.getZoneId());
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/scan-qr")
    public ResponseEntity<ScanResult> scanByQr(@Valid @RequestBody QrScanRequest request) {
        ScanResult result = scanEventService.scanByQr(request.getQrPayload(), request.getZoneId());
        return result.isSuccess() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/validate")
    public ValidationResult validateTicketForZone(@RequestBody ValidateRequest request) {
        return scanEventService.validateTicketForZone(request.getTicketId(), request.getZoneId());
    }

    @GetMapping("/ticket/{ticketId}")
    public List<ScanEventResponse> getScanHistoryForTicket(@PathVariable UUID ticketId) {
        return scanEventService.getScanHistoryForTicket(ticketId).stream()
                .map(ScanEventResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/zone/{zoneId}")
    public Page<ScanEventResponse> getScanHistoryForZone(@PathVariable UUID zoneId, Pageable pageable) {
        return scanEventService.getScanHistoryForZone(zoneId, pageable).map(ScanEventResponse::from);
    }

    @GetMapping("/event/{eventId}")
    public Page<ScanEventResponse> getScanHistoryForEvent(@PathVariable UUID eventId, Pageable pageable) {
        return scanEventService.getScanHistoryForEvent(eventId, pageable).map(ScanEventResponse::from);
    }

    @GetMapping("/count/ticket/{ticketId}/zone/{zoneId}")
    public Integer getTicketZoneEntryCount(@PathVariable UUID ticketId, @PathVariable UUID zoneId) {
        return scanEventService.getEntryCountForTicketAndZone(ticketId, zoneId);
    }
}
