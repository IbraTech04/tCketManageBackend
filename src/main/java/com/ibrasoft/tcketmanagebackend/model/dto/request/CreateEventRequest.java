package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
public class CreateEventRequest {
    @NotBlank
    private String name;
    @NotNull
    private OffsetDateTime time;
    @NotBlank
    private String location;
    @NotBlank
    private String description;
}
