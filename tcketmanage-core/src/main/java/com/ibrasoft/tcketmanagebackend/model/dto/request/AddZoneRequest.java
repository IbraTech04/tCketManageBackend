package com.ibrasoft.tcketmanagebackend.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddZoneRequest {
    private String zoneName;
}