package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for scan operations: the outcome of scanning a ticket and, on success, the
 * recorded scan event.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanResult {
    private boolean success;
    private String message;
    private ScanEventResponse scanEvent;
}
