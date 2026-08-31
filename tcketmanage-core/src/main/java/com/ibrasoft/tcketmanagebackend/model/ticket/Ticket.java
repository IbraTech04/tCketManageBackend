package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import jakarta.validation.constraints.Email;

import java.time.Instant;
import java.util.UUID;

/**
 * POJO class representing a Ticket entity.
 */
@Data
@Table(name = "tcket:tickets",
       indexes = @Index(name = "idx_ticket_holder_ref", columnList = "holder_ref"))
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    /**
     * The unique identifier for the ticket. This ID acts as both a primary key for the database,
     * and a value to be used in QR Code Generation
     */
    @Id
    private UUID ID;

    /**
     * The event this ticket is for. EAGER fetch for async ticket email sender
     */
    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
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

    /**
     * The type this ticket was issued as. EAGER for the same reason as {@link #event}
     */
    @ManyToOne
    @JoinColumn(name = "ticket_type_id", nullable = false)
    @NotNull
    private TicketType ticketType;

    @Column(name = "holder_ref", length = 200)
    private String holderRef;

    /**
     * Lifecycle state of the ticket. Defaults to {@code ACTIVE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    /**
     * When this ticket was last successfully emailed to its holder, or {@code null} if it has never
     * been sent. Drives the "send missing" delivery flow and lets operators see delivery status.
     */
    @Column(name = "last_ticket_sent")
    private Instant lastTicketSent;
}
