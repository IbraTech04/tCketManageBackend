package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent error envelope returned by {@code GlobalExceptionHandler}.
 */
@Data
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private String message;
    private int status;
    private Instant timestamp;
    private String path;

    /** Field-level validation errors, present only for validation failures. */
    private Map<String, String> fieldErrors;
}
