package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.service.email.EmailJobState;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Live, cumulative status of a bulk-email job, returned in full by the snapshot endpoint
 * ({@code GET /tcket/email-jobs/{jobId}}) so a late or reconnecting subscriber can catch up — the
 * simple broker does not replay missed messages.
 *
 * <p>The STOMP feed at {@code /topic/email-jobs/{jobId}} carries the {@link Broadcast} projection of
 * this object instead of the object itself; see there for why the two views differ.
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

    /**
     * Consistent snapshot of this job as it goes out over STOMP.
     *
     * <p>Taken under the same lock as {@link #record(String, boolean)} and {@link #complete()}, so a
     * subscriber never sees {@code processed} advanced without the matching counter.
     */
    public synchronized Broadcast broadcastView() {
        return new Broadcast(jobId, type, total, state, processed, sent, failed, lastSuccess,
                startedAt, finishedAt);
    }

    /**
     * What the STOMP feed carries: this status minus {@link EmailJobStatus#getLastEmail()}.
     *
     * <p>SECURITY: a broadcast reaches every subscriber to the destination, and the recipient
     * address of each ticket as it is processed amounts to the event's attendee roster streamed out
     * one address at a time during a bulk resend. The snapshot endpoint keeps the field — it is
     * gated on {@code @tcketmanageAuthz.canManageEvents()} and answers one caller who asked for one
     * job — but the feed has no business carrying it.
     *
     * <p>{@code lastSuccess} stays: it is what drives the live "that one failed" indicator, and it
     * says nothing about who the ticket belonged to. A client that wants the address alongside it
     * reads the snapshot endpoint.
     *
     * <p>A separate projection rather than a Jackson {@code @JsonView}: the view would have to be
     * threaded through the message converter's serialization hints, which is a global change to how
     * the host's broker serializes every payload, to redact one field of one message type.
     */
    public record Broadcast(UUID jobId,
                            String type,
                            int total,
                            EmailJobState state,
                            int processed,
                            int sent,
                            int failed,
                            Boolean lastSuccess,
                            Instant startedAt,
                            Instant finishedAt) {
    }
}
