package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ImportConfig;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ImportResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import lombok.AllArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imports an attendee CSV into an existing event, creating immediately-scannable {@code ACTIVE}
 * tickets with no order or payment. Resolution of the ticket type per row comes from the configured
 * ticket-type column (by name within the event) or a default type. Import is all-or-nothing: if any
 * row is invalid, nothing is persisted and the row errors are returned for the operator to fix.
 */
@Service
@AllArgsConstructor
public class ImportService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final InventoryService inventoryService;

    @Transactional
    public ImportResult importAttendees(UUID eventId, MultipartFile file, ImportConfig cfg) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (cfg.getTicketTypeColumn() == null && cfg.getDefaultTicketTypeId() == null) {
            throw new IllegalArgumentException(
                "Provide either ticketTypeColumn or defaultTicketTypeId");
        }

        Map<String, TicketType> typesByName = new HashMap<>();
        for (TicketType type : ticketTypeRepository.findByEvent_Id(eventId)) {
            typesByName.put(type.getName().trim().toLowerCase(), type);
        }

        TicketType defaultType = null;
        if (cfg.getDefaultTicketTypeId() != null) {
            defaultType = ticketTypeRepository.findById(cfg.getDefaultTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Default ticket type not found"));
            if (defaultType.getEvent() == null || !defaultType.getEvent().getId().equals(eventId)) {
                throw new IllegalArgumentException("Default ticket type does not belong to this event");
            }
        }

        List<ImportResult.RowError> errors = new ArrayList<>();
        List<Ticket> toSave = new ArrayList<>();
        Map<UUID, Integer> perTypeCount = new HashMap<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {
            int rowNum = 0;
            for (CSVRecord record : parser) {
                rowNum++;
                if (cfg.isHasHeaderRow() && rowNum == 1) {
                    continue;
                }
                try {
                    String firstName = column(record, cfg.getFirstNameColumn());
                    String lastName = column(record, cfg.getLastNameColumn());
                    String email = column(record, cfg.getEmailColumn());
                    if (isBlank(firstName) || isBlank(lastName) || isBlank(email)) {
                        throw new IllegalArgumentException("Missing required attendee field");
                    }
                    TicketType type = resolveType(record, cfg, typesByName, defaultType);
                    toSave.add(Ticket.builder()
                            .ID(UUID.randomUUID())
                            .firstName(firstName.trim())
                            .lastName(lastName.trim())
                            .email(email.trim())
                            .event(event)
                            .ticketType(type)
                            .status(TicketStatus.ACTIVE)
                            .build());
                    perTypeCount.merge(type.getId(), 1, Integer::sum);
                } catch (IllegalArgumentException rowError) {
                    errors.add(new ImportResult.RowError(rowNum, rowError.getMessage()));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read CSV file: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            // Reserve capacity (may throw ConflictException → 409 and roll back) then persist.
            perTypeCount.forEach(inventoryService::reserve);
            ticketRepository.saveAll(toSave);
        }

        return ImportResult.builder()
                .imported(errors.isEmpty() ? toSave.size() : 0)
                .errors(errors)
                .build();
    }

    private TicketType resolveType(CSVRecord record, ImportConfig cfg,
                                   Map<String, TicketType> typesByName, TicketType defaultType) {
        if (cfg.getTicketTypeColumn() != null) {
            String name = column(record, cfg.getTicketTypeColumn());
            if (!isBlank(name)) {
                TicketType type = typesByName.get(name.trim().toLowerCase());
                if (type == null) {
                    throw new IllegalArgumentException("Unknown ticket type: " + name);
                }
                return type;
            }
        }
        if (defaultType != null) {
            return defaultType;
        }
        throw new IllegalArgumentException("No ticket type for row and no default configured");
    }

    private String column(CSVRecord record, int index) {
        if (index < 0 || index >= record.size()) {
            throw new IllegalArgumentException("Column index " + index + " is out of range");
        }
        return record.get(index);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
