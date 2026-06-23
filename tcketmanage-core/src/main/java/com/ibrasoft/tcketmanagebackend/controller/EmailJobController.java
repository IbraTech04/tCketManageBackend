package com.ibrasoft.tcketmanagebackend.controller;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.response.EmailJobStatus;
import com.ibrasoft.tcketmanagebackend.service.email.EmailJobRegistry;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Snapshot view of an async email job. Complements the live STOMP feed at
 * {@code /topic/email-jobs/{jobId}}: a client that subscribes late, reconnects, or simply prefers
 * polling can read the current cumulative status here. Job state is in-memory and ephemeral, so an
 * unknown id (or one evicted after a restart) returns 404.
 */
@RestController
@RequestMapping("/tcket/email-jobs")
@AllArgsConstructor
public class EmailJobController {

    private final EmailJobRegistry registry;

    @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")
    @GetMapping("/{jobId}")
    public EmailJobStatus getJob(@PathVariable UUID jobId) {
        return registry.get(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Email job not found"));
    }
}
