package com.ibrasoft.tcketmanagebackend.service.email;

import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of recent bulk-email jobs, so operators can poll a job's status (snapshot endpoint)
 * in addition to the live STOMP feed. State is deliberately ephemeral — it is progress reporting, not
 * a system of record, and is lost on restart (delivery itself is durably reflected by each ticket's
 * {@code lastTicketSent}).
 *
 * <p>Bounded by {@link #MAX_JOBS}: once exceeded, the oldest-started job is evicted so a long-running
 * server doesn't accumulate job records without limit.
 */
@Component
public class EmailJobRegistry {

    private static final int MAX_JOBS = 200;

    private final Map<UUID, EmailJobStatus> jobs = new ConcurrentHashMap<>();

    public EmailJobStatus create(String type, int total) {
        EmailJobStatus status = new EmailJobStatus(UUID.randomUUID(), type, total);
        jobs.put(status.getJobId(), status);
        evictIfNeeded();
        return status;
    }

    public Optional<EmailJobStatus> get(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    private void evictIfNeeded() {
        while (jobs.size() > MAX_JOBS) {
            jobs.values().stream()
                    .min((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                    .ifPresent(oldest -> jobs.remove(oldest.getJobId()));
        }
    }
}
