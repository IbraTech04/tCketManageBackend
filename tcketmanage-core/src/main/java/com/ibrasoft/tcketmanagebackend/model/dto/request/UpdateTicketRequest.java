package com.ibrasoft.tcketmanagebackend.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for updating an existing ticket
 * Contains fields that can be modified on an existing ticket
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketRequest {
    // Getters and setters
    private String firstName;
    private String lastName;
    private String email;
    private UUID ticketTypeId;
}