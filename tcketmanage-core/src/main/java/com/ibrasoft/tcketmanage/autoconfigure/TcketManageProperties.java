package com.ibrasoft.tcketmanage.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Top-level tCketManage settings, bound from {@code tcketmanage.*}.
 */
@ConfigurationProperties(prefix = "tcketmanage")
@Data
public class TcketManageProperties {

    /**
     * Whether the embedded tCketManage core is active. Off by default so a host opts in explicitly.
     *
     * <p>Read by {@code @ConditionalOnProperty} on {@link TcketManageAutoConfiguration} rather than
     * from this object — a condition is evaluated long before any properties bean exists. Declared
     * here so it appears in the generated configuration metadata like every other setting.
     */
    private boolean enabled = false;

    /**
     * URL prefix every core endpoint is mounted under.
     *
     * <p>Configurable because a host's security rules, CORS configuration and reverse proxy are
     * usually organised around one API namespace, and core's endpoints have to be able to live
     * inside it. LensBridge, for example, routes everything through {@code /api/**}, so it sets this
     * to {@code /api/tcket} and core's paths fall under rules that already exist.
     *
     * <p>Controllers resolve this through a {@code ${tcketmanage.base-path:/tcket}} placeholder, so
     * the default here and the default in those annotations must stay in step.
     */
    private String basePath = "/tcket";

    private final Cors cors = new Cors();

    private final Websocket websocket = new Websocket();

    @Data
    public static class Websocket {

        /**
         * Broker destination prefix core publishes email-job progress under; the full destination a
         * client subscribes to is {@code <topic-prefix>/email-jobs/{jobId}}.
         *
         * <p>Configurable because core no longer configures the message broker — the host does (see
         * {@link com.ibrasoft.tcketmanagebackend.config.WebSocketConfig}), and a host is free to
         * enable a broker on prefixes other than {@code /topic}. Publishing to a destination the
         * broker does not recognise is the worst kind of failure available here: the handler drops
         * the message without an exception, so the resend endpoint still answers 202 and the
         * operator UI waits forever on a stream that will never arrive. A mismatch is therefore
         * rejected at startup by {@code WebSocketDestinationRequirement} instead.
         */
        private String topicPrefix = "/topic";

        /** {@link #topicPrefix} without a trailing slash, so callers can append their own. */
        public String normalizedTopicPrefix() {
            String prefix = topicPrefix == null ? "" : topicPrefix.trim();
            while (prefix.endsWith("/")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            return prefix;
        }

        /** Destination progress snapshots for {@code jobId} are published to. */
        public String emailJobDestination(Object jobId) {
            return normalizedTopicPrefix() + "/email-jobs/" + jobId;
        }
    }

    @Data
    public static class Cors {

        /**
         * Origins permitted to call core's endpoints. Applied to {@link #basePath} only — core does
         * not configure CORS for paths it does not own.
         *
         * <p>Note that this is Spring MVC-level CORS. A host running Spring Security handles CORS in
         * its filter chain, which runs first and will reject a cross-origin preflight before MVC
         * ever sees it; such a host must add core's base path to its own
         * {@code CorsConfigurationSource} instead.
         */
        private List<String> allowedOrigins = List.of("*");
    }
}
