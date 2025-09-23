package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CSVIndexMatte;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TicketService {

    private TicketRepository ticketRepository;

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
                    .zonePermissions(1L) // Default zone permission; Zone 1 => Main Entrance
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
                .zonePermissions(1) // Default zone permission; Zone 1 => Main Entrance
                .build();
        return ticketRepository.save(ticket);
    }

    public Optional<Ticket> findTicketById(UUID id) {
        return ticketRepository.findById(id);
    }

    /**
     * Add zone permission to a ticket by setting the corresponding bit in the zonePermissions field.
     * @param ticket The ticket to update.
     * @param zoneBit The Event Zone ID (bit position) to grant access to. This can be any number from 0 to 63.
     */
    public void addZonePermission(Ticket ticket, int zoneBit) {
        if (zoneBit < 0 || zoneBit > 63) {
            throw new IllegalArgumentException("Zone bit must be between 0 and 63.");
        }
        if (ticket.getEvent().getZones().size() <= zoneBit) {
            throw new IllegalArgumentException("Zone bit " + zoneBit + " does not exist for this event.");
        }
        long currentPermissions = ticket.getZonePermissions();
        long newPermissions = currentPermissions | (1L << zoneBit);
        ticket.setZonePermissions(newPermissions);
        ticketRepository.save(ticket);
    }

    public void addZonePermission(UUID ticketId, int zoneBit) {
        Optional<Ticket> optionalTicket = ticketRepository.findById(ticketId);
        if (optionalTicket.isPresent()) {
            Ticket ticket = optionalTicket.get();
            addZonePermission(ticket, zoneBit);
        } else {
            throw new IllegalArgumentException("Ticket with ID " + ticketId + " not found.");
        }
    }

    public boolean hasZonePermission(Ticket ticket, int zoneBit) {
        if (zoneBit < 0 || zoneBit > 63) {
            throw new IllegalArgumentException("Zone bit must be between 0 and 63.");
        }
        long currentPermissions = ticket.getZonePermissions();
        return (currentPermissions & (1L << zoneBit)) != 0;
    }

}
