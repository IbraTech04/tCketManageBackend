package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.UpdateTicketRequest;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.event.Zone;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.model.ticket.event.ScanEvent;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.ScanEventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
@Transactional
public class TicketService {

    private TicketRepository ticketRepository;
    private ScanEventRepository scanEventRepository;
    private TicketTypeRepository ticketTypeRepository;
    private EventRepository eventRepository;

    /**
     * Issues a single comp/admin ticket bound to an event and ticket type (outside the order and
     * import flows). The ticket type must belong to the given event.
     */
    public Ticket createTicket(String firstName, String lastName, String email,
                               UUID eventId, UUID ticketTypeId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));

        if (ticketType.getEvent() == null || !ticketType.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException("Ticket type does not belong to the specified event");
        }

        Ticket ticket = Ticket.builder()
                .ID(UUID.randomUUID())
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .event(event)
                .ticketType(ticketType)
                .build();
        return ticketRepository.save(ticket);
    }

    public Ticket updateTicket(UUID id, UpdateTicketRequest request) {
        Ticket existing = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        existing.setFirstName(request.getFirstName());
        existing.setLastName(request.getLastName());
        existing.setEmail(request.getEmail());

        if (request.getTicketTypeId() != null) {
            TicketType ticketType = ticketTypeRepository.findById(request.getTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("TicketType not found"));
            existing.setTicketType(ticketType);
        }

        return ticketRepository.save(existing);
    }

    public boolean deleteTicket(UUID id) {
        if (ticketRepository.existsById(id)) {
            ticketRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findTicketById(UUID id) {
        return ticketRepository.findById(id);
    }

    public void recordTicketScan(Ticket ticket, Zone zone) {
        scanEventRepository.save(ScanEvent.builder()
                .ticketId(ticket.getID())
                .zone(zone)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public int getZoneEntryCount(Ticket ticket, Zone zone) {
        return scanEventRepository.countZoneEntriesByTicketId(ticket.getID(), zone.getId());
    }

    @Transactional(readOnly = true)
    public Page<Ticket> getTicketsByEvent(UUID id, Pageable pageable) {
        return ticketRepository.findByEvent(id, pageable);
    }
}
