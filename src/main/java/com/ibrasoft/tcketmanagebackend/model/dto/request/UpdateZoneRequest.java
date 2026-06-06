package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating a zone. Replaces binding the raw {@code Zone} entity.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateZoneRequest {

    @NotBlank
    private String name;

    @NotNull
    private String description;
}
