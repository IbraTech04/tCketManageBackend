package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CSVIndexMatte;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEventId;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.ZoneRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TicketService {

    private TicketRepository ticketRepository;
    private ScanEventRepository scanEventRepository;
    private ZoneRepository zoneRepository;

    public void importTicketsFromCSV(List<List<String>> csvData, Event event, CSVIndexMatte matte) {
        for (List<String> row : csvData) {
            String firstName = row.get(matte.getFirstNameIndex());
            String lastName = row.get(matte.getLastNameIndex());
            String email = row.get(matte.getEmailIndex());
            Ticket ticket = Ticket.builder()
                    .ID(UUID.randomUUID())
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .event(event)
                    .build();
            ticketRepository.save(ticket);
        }
    }

    public Ticket createTicket(String firstName, String lastName, String email) {
        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .build();
        return ticketRepository.save(ticket);
    }

    public Optional<Ticket> findTicketById(UUID id) {
        return ticketRepository.findById(id);
    }

    public void recordTicketScan(Ticket ticket, Zone zone){
        ScanEvent scanEvent = ScanEvent.builder()
                .id(new ScanEventId(ticket.getID(), System.currentTimeMillis()))
                .zone(zone)
                .build();
        scanEventRepository.save(scanEvent);
    }

    public boolean hasZonePermission(Ticket ticket, UUID zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found"));
        int zoneBitPosition = zone.getBitPosition();
        int permissionMask = 1 << zoneBitPosition;
        return (ticket.getTicketType().getZonePermissions() & permissionMask) != 0;
    }


    public void validateTicket(Ticket ticket, Zone zone) {
        if (!hasZonePermission(ticket, zone.getId())) {
            throw new SecurityException("Ticket does not have permission to enter zone: " + zone.getName());
        }
        int entryCount = scanEventRepository.countZoneEntriesByTicketId(ticket.getID(), zone.getId());
        if (entryCount > 0) {
            throw new SecurityException("Ticket has already been used to enter zone: " + zone.getName());
        }
        recordTicketScan(ticket, zone);
    }

    public Page<Ticket> getTicketsByEvent(UUID id, Pageable pageable) {
        return ticketRepository.findByEvent(id, pageable);
    }
}
