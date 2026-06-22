package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketTypeRequest;
import com.ibrasoft.tcketmanagebackend.model.dto.response.TicketTypeResponse;
import com.ibrasoft.tcketmanagebackend.service.TicketTypeService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * Individual ticket types. Creation is a sub-resource of an event
 * ({@code POST /events/{id}/ticket-types}).
 */
@RestController
@RequestMapping("/api/v1/ticket-types")
@AllArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @GetMapping
    public Page<TicketTypeResponse> getAllTicketTypes(Pageable pageable) {
        return ticketTypeService.getAllTicketTypes(pageable).map(TicketTypeResponse::from);
    }

    @GetMapping("/{id}")
    public TicketTypeResponse getTicketTypeById(@PathVariable UUID id) {
        return TicketTypeResponse.from(ticketTypeService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found")));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @PutMapping("/{id}")
    public TicketTypeResponse updateTicketType(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateTicketTypeRequest request) {
        return TicketTypeResponse.from(ticketTypeService.updateTicketType(id, request));
    }

    @PreAuthorize("hasRole(@tcketmanageRoles.admin)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable UUID id) {
        return ticketTypeService.deleteTicketType(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
