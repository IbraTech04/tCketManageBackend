package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One seat being purchased: a ticket type and the attendee it's for.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemRequest {

    @NotNull
    private UUID ticketTypeId;

    @NotBlank
    private String attendeeFirstName;

    @NotBlank
    private String attendeeLastName;

    @Email
    @NotBlank
    private String attendeeEmail;
}
