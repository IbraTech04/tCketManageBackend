package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to add a zone to an existing event.
 *
 * <p>SECURITY: {@code zoneName} is copied straight into {@code Zone.name} <em>and</em>
 * {@code Zone.description} by {@code EventService.addZoneToEvent}, and the entity constrains that
 * column to {@code @Size(min = 1, max = 20) @NotBlank}. Mirroring the constraint here means an
 * over-long or blank name comes back as a 400 naming the field, instead of a Hibernate
 * {@code ConstraintViolationException} at flush time — which arrives as a 500 after the event
 * aggregate has already been mutated in the persistence context.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddZoneRequest {

    @NotBlank
    @Size(max = 20)
    private String zoneName;
}