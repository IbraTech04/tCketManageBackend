package com.ibrasoft.tcketmanagebackend.model.event;

import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * POJO class representing an Event entity.
 *
 * This represents event metadata, such as event name, date, location, etc.
 */
@Builder
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "events")
public class Event {
    @Id
    private UUID id;

    /**
     * The name of the event.
     */
    @NotNull
    private String name;

    /**
     * The date and time of the event, in the local timezone of the event location.
     */
    @NotNull
    private LocalDateTime time;

    /**
     * Event location, e.g., "IB 120"
     */
    @NotBlank
    private String location;

    /**
     * A brief description of the event.
     */
    @NotBlank
    private String description;

    @OneToMany(mappedBy = "event")
    @Size(min = 1, max = 64)
    private List<Zone> zones;

    @OneToMany(mappedBy = "event")
    private List<Ticket> tickets;
}
