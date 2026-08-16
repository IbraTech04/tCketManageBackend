package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.Data;

import java.util.UUID;

/**
 * Immediate {@code 202 Accepted} response for an async email job. The client subscribes to
 * {@code /topic/email-jobs/{jobId}} (and/or polls {@code GET /tcket/email-jobs/{jobId}}) to follow
 * progress to completion.
 */
@Data
public class EmailJobAccepted {

    private final UUID jobId;

    /** Tickets queued for delivery, so the UI can render a progress bar before any updates arrive. */
    private final int total;
}
