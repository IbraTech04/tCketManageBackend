package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outcome of a CSV attendee import. On success {@code imported} is the number of tickets created and
 * {@code errors} is empty; on failure nothing is persisted and {@code errors} lists per-row problems.
 */
@Data
@Builder
@AllArgsConstructor
public class ImportResult {

    private int imported;
    private List<RowError> errors;

    @Data
    @AllArgsConstructor
    public static class RowError {
        private int row;
        private String reason;
    }
}
