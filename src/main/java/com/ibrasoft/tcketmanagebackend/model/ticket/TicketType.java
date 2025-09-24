package com.ibrasoft.tcketmanagebackend.model.ticket;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketType {

    @Id
    private UUID id;

    @NotBlank
    private String name;

    @NotNull
    private Long zonePermissions = 1L; // Default to 1 (access to Zone 0 - Main Entrance)

}
