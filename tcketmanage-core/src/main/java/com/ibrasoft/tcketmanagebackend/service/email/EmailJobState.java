package com.ibrasoft.tcketmanagebackend.service.email;

/**
 * Lifecycle of a tracked bulk-email job. Clients watch {@code /topic/email-jobs/{jobId}} and treat
 * {@link #COMPLETED} as the terminal signal (the final message also carries the {@code sent}/
 * {@code failed} totals that the old synchronous response used to return).
 */
public enum EmailJobState {
    RUNNING,
    COMPLETED
}
