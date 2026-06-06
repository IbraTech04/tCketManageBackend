package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateZoneRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and edits individual zones. Zone <em>creation</em> happens as a sub-resource of an event via
 * {@link EventService#addZoneToEvent}.
 */
@Service
@Transactional
@AllArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public Page<Zone> getAllZones(Pageable pageable) {
        return zoneRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Zone> findById(UUID id) {
        return zoneRepository.findById(id);
    }

    public Zone updateZone(UUID id, UpdateZoneRequest request) {
        Zone existing = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found"));

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());

        return zoneRepository.save(existing);
    }

    public boolean deleteZone(UUID id) {
        if (zoneRepository.existsById(id)) {
            zoneRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
