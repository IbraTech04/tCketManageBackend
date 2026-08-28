package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to add a zone to an existing event.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddZoneRequest {

    @NotBlank
    @Size(max = 20)
    private String zoneName;
}