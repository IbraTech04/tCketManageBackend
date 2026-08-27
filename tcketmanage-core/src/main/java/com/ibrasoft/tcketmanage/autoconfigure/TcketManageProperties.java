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

        /**
         * How hard
         * {@link com.ibrasoft.tcketmanagebackend.config.EmailJobSubscriptionInterceptor} presses on
         * the identity behind a {@code SUBSCRIBE} to the email-job feed.
         *
         * <p>Only the principal half of that guard is configurable. The destination-shape half —
         * a subscription may name one concrete job id and may not fan out across the feed — is
         * unconditional, because it needs no principal and is what stops an unauthenticated client
         * from vacuuming up every job's snapshots.
         */
        private SubscriptionAuthorization subscriptionAuthorization = SubscriptionAuthorization.AUTO;

        /**
         * Modes for {@link Websocket#getSubscriptionAuthorization()}.
         *
         * <p>{@link #AUTO} is the default because core cannot assume the application has wired
         * authentication into its websocket handshake at all — the standalone {@code tcketmanage-app}
         * has not, and its progress UI would stop working under an unconditional gate. See
         * {@link com.ibrasoft.tcketmanagebackend.config.EmailJobSubscriptionInterceptor} for the full
         * reasoning and the alternatives that were rejected.
         */
        public enum SubscriptionAuthorization {

            /**
             * Apply {@code @tcketmanageAuthz.canManageEvents()} when the STOMP session carries a
             * principal; skip it when the session carries none.
             */
            AUTO,

            /**
             * Always apply the check, and refuse a session that carries no principal at all. The
             * setting for a host that permits an unauthenticated handshake but still wants the feed
             * closed.
             */
            REQUIRED,

            /**
             * Skip the principal check entirely. An escape hatch for a deployment whose
             * {@link com.ibrasoft.tcketmanagebackend.security.TcketManageAuthorizer} cannot be
             * evaluated off a request thread; the destination-shape check still applies.
             */
            DISABLED
        }

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
         *
         * <p>SECURITY: empty by default, which permits same-origin traffic only. This used to
         * default to {@code ["*"]} — every website on the internet could call core's endpoints and
         * open core's websocket, in a library whose whole premise is that the host supplies the
         * authentication. ({@code allowCredentials} is never set, so this was never the
         * {@code "*"}-plus-credentials variant of the bug; the exposure was the reach, not the
         * cookie.) A permissive default is the wrong shape for the one setting a deployment cannot
         * discover it needed: getting it wrong the other way costs a browser console error on first
         * use, not a silent leak.
         *
         * <p>This is a breaking change for any deployment that relied on the wildcard. Such a
         * deployment restores it by naming its front-end origins explicitly —
         * {@code tcketmanage.cors.allowed-origins=https://app.example.org} — which is what it should
         * have been carrying anyway. The standalone app's {@code application.properties.example}
         * still sets a value, so a deployment that started from the example is unaffected.
         */
        private List<String> allowedOrigins = List.of();
    }
}
