package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateZoneRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ZoneResponse;
import com.ibrasoft.tcketmanagebackend.service.ZoneService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Individual zones. Zone creation is a sub-resource of an event ({@code POST /events/{id}/zones}).
 */
@RestController
@RequestMapping("/tcket/zones")
@AllArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        return zoneService.getAllZones(pageable).map(ZoneResponse::from);
    }

    @GetMapping("/{id}")
    public ZoneResponse getZoneById(@PathVariable UUID id) {
        return ZoneResponse.from(zoneService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found")));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PutMapping("/{id}")
    public ZoneResponse updateZone(@PathVariable UUID id, @Valid @RequestBody UpdateZoneRequest request) {
        return ZoneResponse.from(zoneService.updateZone(id, request));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        return zoneService.deleteZone(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
