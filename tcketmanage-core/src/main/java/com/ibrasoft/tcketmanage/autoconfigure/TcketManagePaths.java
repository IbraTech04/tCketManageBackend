package com.ibrasoft.tcketmanage.autoconfigure;

import java.util.stream.Stream;

public class TcketManagePaths {

    private final String basePath;

    TcketManagePaths(String basePath) {
        this.basePath = basePath;
    }

    /**
     * Readable without authentication: event browsing and the buyer's view of their own order or
     * ticket (ownership enforced per entity, see the class javadoc).
     */
    public String[] publicGetPatterns() {
        return prefixed(
                "/events",
                "/events/*",
                "/events/*/zones",
                "/events/*/ticket-types",
                "/zones",
                "/zones/*",
                "/ticket-types",
                "/ticket-types/*",
                "/orders/*",
                "/tickets/*");
    }

    /**
     * Writable without authentication: guest checkout, buyer self-cancellation, and inbound payment
     * provider webhooks (called by the provider, which has no session — those verify themselves by
     * provider signature, not by login).
     */
    public String[] publicPostPatterns() {
        return prefixed(
                "/orders",
                "/orders/*/cancel",
                "/payments/*/webhook");
    }

    /** Every core endpoint, for a host that wants one rule covering the whole mount point. */
    public String[] allPatterns() {
        return prefixed("/**");
    }

    /** The configured mount point, e.g. {@code /tcket}. */
    public String basePath() {
        return basePath;
    }

    private String[] prefixed(String... suffixes) {
        return Stream.of(suffixes).map(s -> basePath + s).toArray(String[]::new);
    }
}
