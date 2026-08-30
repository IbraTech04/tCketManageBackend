package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties.Websocket.SubscriptionAuthorization;
import com.ibrasoft.tcketmanagebackend.security.AuthorizationGateway;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;

import java.security.Principal;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Guards {@code SUBSCRIBE} frames aimed at core's email-job progress feed
 * ({@code <topic-prefix>/email-jobs/{jobId}}).
 *
 * <p>Without this, the feed had no access control of any kind. The REST twin,
 * {@code GET <base-path>/email-jobs/{jobId}}, is gated on {@code @tcketmanageAuthz.canManageEvents()},
 * but the STOMP path bypassed that gate entirely: the simple broker matches subscription
 * destinations with {@link AntPathMatcher}, so any client that could open the websocket could
 * {@code SUBSCRIBE /topic/email-jobs/*} — or simply {@code /topic/**} — and receive every job's
 * snapshot, including the recipient address of each ticket as it was sent. A bulk resend streamed
 * the whole attendee roster to anyone listening.
 *
 * <p>Two independent checks are applied, in this order.
 *
 * <h2>1. Destination shape (always on)</h2>
 *
 * A subscription is allowed to name exactly one job: the destination must be
 * {@code <topic-prefix>/email-jobs/<uuid>}. Anything that would fan out across the namespace —
 * {@code /topic/email-jobs/*}, {@code /topic/**}, {@code /topic/{a}/{b}}, {@code /topic/#} — is
 * refused. This check needs no principal and is therefore unconditional; it is the half of the fix
 * that works in every deployment, including ones with no authentication at all, and it is not
 * affected by {@link SubscriptionAuthorization#DISABLED}.
 *
 * <p>Destinations outside core's namespace are passed through untouched. Core is one
 * {@code WebSocketMessageBrokerConfigurer} among several in an embedding host, and a host's own
 * {@code /topic/whatever/**} subscriptions are its own business — this interceptor must not become
 * a general-purpose wildcard ban on someone else's broker. "Outside the namespace" is decided by
 * matching the client's destination, treated as a pattern, against a probe email-job destination:
 * if the pattern cannot reach core's feed, core has no opinion on it. (A pattern crafted to match
 * some job ids but not the probe — and which does not sit under the email-jobs prefix, where the
 * uuid check catches it — could slip past. No such pattern is expressible with the wildcards
 * {@code AntPathMatcher} supports, but the limitation is worth stating.)
 *
 * <h2>2. Principal (mode-dependent)</h2>
 *
 * A concrete destination is then put through the same gate as the REST endpoint,
 * {@link AuthorizationGateway#canManageEvents()}. Two subtleties:
 *
 * <ul>
 *   <li>The client inbound channel is serviced by a thread pool, so {@link SecurityContextHolder}
 *       is empty on it unless the host installed Spring Security's own messaging interceptors.
 *       Core's authorizers read the holder, so this interceptor installs the session's
 *       {@link Authentication} for the duration of the check and restores the thread to exactly the
 *       state it found it in — leaving a stale context on a pooled thread would be worse than the
 *       bug being fixed.</li>
 *   <li>A principal that is present but is not an {@link Authentication} still counts as "this
 *       connection is authenticated": the check runs against an empty context and therefore fails
 *       closed, rather than being silently skipped.</li>
 * </ul>
 *
 * <h2>Why the principal check is not unconditional</h2>
 *
 * Core's {@code @PreAuthorize} annotations are deliberately inert in the standalone
 * {@code tcketmanage-app}, because nothing there enables method security (see {@code docs/AUTH.MD}).
 * This interceptor is programmatic, so it would be live there — and
 * {@link com.ibrasoft.tcketmanagebackend.security.RoleBasedAuthorizer} fails closed on a null or
 * anonymous authentication. A flat {@code canManageEvents()} gate would therefore reject every
 * subscription in the standalone app and break its bulk-email progress UI outright, which is a
 * behaviour change core has no business making on a host's behalf and does not match the documented
 * posture of every other authorization point in the library.
 *
 * <p>So the default, {@link SubscriptionAuthorization#AUTO}, enforces the gate whenever the STOMP
 * session carries a principal and skips it when the session carries none. In a host that
 * authenticates its websocket handshake (LensBridge) every session has a principal, so the gate is
 * always in force; in the standalone app no session ever does, so the progress UI keeps working and
 * the shape check above still stops the roster-wide subscribe. The rejected alternatives were:
 * enforcing unconditionally (breaks the standalone app, and any host that has not yet wired
 * authentication into its handshake, with no diagnosis available from the client side beyond an
 * ERROR frame); and doing nothing when no principal is present with no way to say otherwise (leaves
 * a host that permits an unauthenticated handshake exposed with no remedy short of forking).
 * {@link SubscriptionAuthorization#REQUIRED} is the remedy for that host — it demands a principal —
 * and {@link SubscriptionAuthorization#DISABLED} is the escape hatch for a deployment whose custom
 * {@link com.ibrasoft.tcketmanagebackend.security.TcketManageAuthorizer} cannot be evaluated off a
 * request thread.
 */
public class EmailJobSubscriptionInterceptor implements ChannelInterceptor {

    /**
     * Canonical RFC 4122 form. Deliberately stricter than {@link java.util.UUID#fromString(String)},
     * which still accepts under-length groups such as {@code 1-1-1-1-1}; a destination is a broker
     * matching key, so it is compared literally and only the exact form the publisher emits can ever
     * receive anything.
     */
    private static final Pattern JOB_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Stand-in job id used to ask "could this destination reach the email-job feed?". */
    private static final String PROBE_JOB_ID = "00000000-0000-4000-8000-000000000000";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final TcketManageProperties properties;

    /**
     * Resolved on use rather than injected, so that building the messaging infrastructure — which
     * collects every {@code WebSocketMessageBrokerConfigurer} in the context, core's
     * {@link WebSocketConfig} included — does not drag the authorization gateway and the host's
     * {@code TcketManageAuthorizer} into initialisation ahead of their turn.
     */
    private final Supplier<AuthorizationGateway> authorization;

    public EmailJobSubscriptionInterceptor(TcketManageProperties properties,
                                           Supplier<AuthorizationGateway> authorization) {
        this.properties = properties;
        this.authorization = authorization;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        // Keyed on the simp message type rather than the STOMP command: a SockJS/native client's
        // subscribe arrives with the type set and, depending on the sub-protocol handler, no command.
        if (accessor.getMessageType() != SimpMessageType.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        String namespace = properties.getWebsocket().normalizedTopicPrefix() + "/email-jobs";
        String prefix = namespace + "/";

        if (destination.startsWith(prefix)) {
            String jobId = destination.substring(prefix.length());
            if (!JOB_ID.matcher(jobId).matches()) {
                // Covers "/topic/email-jobs/*", "/topic/email-jobs/**", "/topic/email-jobs/a*" and
                // any other pattern rooted in the namespace, plus nested destinations.
                throw denied("subscription destination must name a single job id: " + destination);
            }
            authorize(accessor, destination);
            return message;
        }

        if (reachesFeed(destination, prefix)) {
            // A pattern rooted above the namespace: "/topic/**", "/**", "/topic/{a}/{b}".
            throw denied("subscription destination fans out across the email-job feed: " + destination);
        }

        // SECURITY: not core's namespace, so core does not police it. An embedding host's own
        // topics — including its own wildcard subscriptions — must keep working unchanged.
        return message;
    }

    /** Whether {@code destination}, read as a subscription pattern, can match an email-job feed. */
    private boolean reachesFeed(String destination, String prefix) {
        String probe = prefix + PROBE_JOB_ID;
        if (MATCHER.match(destination, probe)) {
            return true;
        }
        // A relay-backed host may be using a broker whose multi-level wildcard is "#" rather than
        // "**" (RabbitMQ, ActiveMQ). Re-test under that reading. It can only ever reject a
        // destination that, so read, actually reaches core's feed, so a host's unrelated "#"
        // subscriptions are unaffected.
        return destination.indexOf('#') >= 0 && MATCHER.match(destination.replace("#", "**"), probe);
    }

    private void authorize(StompHeaderAccessor accessor, String destination) {
        SubscriptionAuthorization mode = properties.getWebsocket().getSubscriptionAuthorization();
        if (mode == SubscriptionAuthorization.DISABLED) {
            return;
        }

        Principal user = accessor.getUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null && user instanceof Authentication sessionAuthentication) {
            authentication = sessionAuthentication;
        }

        if (authentication == null && user == null) {
            // Nothing authenticated this session. See the class javadoc for why AUTO lets it past.
            if (mode == SubscriptionAuthorization.REQUIRED) {
                throw denied("subscription to " + destination + " requires an authenticated session");
            }
            return;
        }

        if (!canManageEvents(authentication)) {
            throw denied("not permitted to subscribe to " + destination);
        }
    }

    /**
     * Runs the gateway check with {@code authentication} installed, restoring the thread's original
     * security context — including "there wasn't one" — afterwards.
     */
    private boolean canManageEvents(Authentication authentication) {
        SecurityContext previous = SecurityContextHolder.getContext();
        boolean wasEmpty = previous.getAuthentication() == null;
        try {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            return authorization.get().canManageEvents();
        } finally {
            if (wasEmpty) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(previous);
            }
        }
    }

    private AccessDeniedException denied(String reason) {
        return new AccessDeniedException("tCketManage: " + reason);
    }
}
