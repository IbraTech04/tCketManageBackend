package com.ibrasoft.tcketmanagebackend.model.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single seat within an {@link Order}: one ticket type for one named attendee. The price is
 * snapshotted at purchase time so later ticket-type price changes don't alter historical orders.
 */
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "order_items")
public class OrderItem {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @NotBlank
    private String attendeeFirstName;

    @NotBlank
    private String attendeeLastName;

    @Email
    @NotBlank
    private String attendeeEmail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
}
