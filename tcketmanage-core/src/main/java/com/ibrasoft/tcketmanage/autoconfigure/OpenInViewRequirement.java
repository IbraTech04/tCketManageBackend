package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.core.env.Environment;

/**
 * Fail-fast guard: tCketManage core's read endpoints map lazy JPA collections to DTOs <em>after</em>
 * the service transaction commits (see {@code EventResponse.from}, {@code TicketTypeResponse.from},
 * {@code OrderResponse.from}), so they rely on {@code spring.jpa.open-in-view=true}. That is Spring
 * Boot's default, but a host may disable it deliberately. If core were to be embedded into such a
 * host, read endpoints would fail with LazyInitializationException (500) when they attempt to map
 *
 * <p>Rather than fail silently at request time, this bean is constructed during context startup and
 * throws immediately if open-in-view is off, so the misconfiguration is caught at boot. This is an
 * interim requirement until the OSIV refactor (mapping DTOs inside read transactions) lands; remove
 * it once core no longer depends on open-in-view.
 */
class OpenInViewRequirement {

    OpenInViewRequirement(Environment environment) {
        boolean openInView = environment.getProperty("spring.jpa.open-in-view", Boolean.class, true);
        if (!openInView) {
            throw new IllegalStateException(
                    "tCketManage core requires spring.jpa.open-in-view=true, but the host has it disabled. "
                    + "Core's read endpoints map lazy collections to DTOs after the service transaction "
                    + "closes; with open-in-view=false they fail with LazyInitializationException (500). "
                    + "Set spring.jpa.open-in-view=true, or complete the OSIV refactor before embedding.");
        }
    }
}
