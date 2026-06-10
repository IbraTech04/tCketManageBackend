package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.service.email.EmailJobState;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Live, cumulative status of a bulk-email job. The same object is both pushed over STOMP on every
 * update (to {@code /topic/email-jobs/{jobId}}) and returned by the snapshot endpoint
 * ({@code GET /api/v1/email-jobs/{jobId}}) so a late or reconnecting subscriber can catch up — the
 * simple broker does not replay missed messages.
 *
 * <p>A single job is advanced by exactly one worker thread, so its counters are mutated serially;
 * cross-thread readers (the snapshot endpoint) may observe a slightly stale-but-consistent view.
 */
@Data
public class EmailJobStatus {

    /** Opaque id the client subscribes/polls by. */
    private final UUID jobId;

    /** What triggered the job: {@code RESEND_ALL}, {@code SEND_MISSING}, or {@code RESEND_ONE}. */
    private final String type;

    /** Tickets the job will attempt to deliver. */
    private final int total;

    private volatile EmailJobState state = EmailJobState.RUNNING;

    /** Tickets processed so far ({@code sent + failed}). */
    private volatile int processed;

    /** Tickets delivered successfully (and stamped {@code lastTicketSent}). */
    private volatile int sent;

    /** Tickets whose delivery failed (left unstamped for a later retry). */
    private volatile int failed;

    /** Recipient of the most recently processed ticket — drives the live "sending to…" UI. */
    private volatile String lastEmail;

    /** Whether that most recent send succeeded. */
    private volatile Boolean lastSuccess;

    private final Instant startedAt = Instant.now();

    private volatile Instant finishedAt;

    public EmailJobStatus(UUID jobId, String type, int total) {
        this.jobId = jobId;
        this.type = type;
        this.total = total;
    }

    /** Records the outcome of one ticket and advances the counters. */
    public synchronized void record(String email, boolean success) {
        this.lastEmail = email;
        this.lastSuccess = success;
        this.processed++;
        if (success) {
            this.sent++;
        } else {
            this.failed++;
        }
    }

    public synchronized void complete() {
        this.state = EmailJobState.COMPLETED;
        this.finishedAt = Instant.now();
    }
}
