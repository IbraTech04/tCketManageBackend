package com.ibrasoft.tcketmanagebackend.model.event;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "zones")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Zone {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @Size(min = 1, max = 20)
    @NotBlank
    private String name;

    @NotNull
    private String description;

    public static Zone defaultZone() {
        Zone zone = new Zone();
        zone.setId(UUID.randomUUID());
        zone.setName("Default");
        zone.setDescription("Default entry zone");
        return zone;
    }
}
