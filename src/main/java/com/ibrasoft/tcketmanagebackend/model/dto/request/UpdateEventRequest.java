package com.ibrasoft.tcketmanagebackend.model.dto.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateEventRequest {
    private String name;
    private LocalDateTime time;
    private String location;
    private String description;
}