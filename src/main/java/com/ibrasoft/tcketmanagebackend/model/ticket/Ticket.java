package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.ibrasoft.tcketmanagebackend.model.event.Event;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;

import java.util.UUID;

/**
 * POJO class representing a Ticket entity.
 */
@Data
@Table(name = "tickets")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    /**
     * The unique identifier for the ticket.
     *
     * This ID acts as both a primary key for the database, and a value to be used in QR Code Generation
     */
    @Id
    private UUID ID;

    /**
     * The event this ticket is for.
     */
    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    /**
     * The first name of the ticket holder.
     */
    @Size(min = 1, max = 50)
    @NotBlank
    private String firstName;

    /**
     * The last name of the ticket holder.
     */
    @Size(min = 1, max = 50)
    @NotBlank
    private String lastName;

    /**
     * The email address of the ticket holder.
     */
    @Email
    @NotBlank
    private String email;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "ticket_type_id")
    @NotNull
    private TicketType ticketType;


}
