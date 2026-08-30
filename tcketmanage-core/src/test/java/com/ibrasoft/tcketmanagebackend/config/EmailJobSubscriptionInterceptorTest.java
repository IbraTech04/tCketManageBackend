package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties.Websocket.SubscriptionAuthorization;
import com.ibrasoft.tcketmanagebackend.security.AuthorizationGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the two invariants {@link EmailJobSubscriptionInterceptor} exists to hold: a STOMP
 * subscription may name exactly one concrete email-job destination and may not fan out across the
 * feed; and, where the session carries an identity, that identity must pass the same
 * {@code canManageEvents()} gate the REST snapshot endpoint applies.
 *
 * <p>Also pins the two things the interceptor must <em>not</em> do — police destinations belonging
 * to an embedding host, and leave a security context behind on the pooled inbound-channel thread.
 */
@ExtendWith(MockitoExtension.class)
class EmailJobSubscriptionInterceptorTest {

    private static final String JOB = "/topic/email-jobs/1b4e28ba-2fa1-11d2-883f-0016d3cca427";

    @Mock private AuthorizationGateway authz;

    private final TcketManageProperties properties = new TcketManageProperties();

    private EmailJobSubscriptionInterceptor interceptor() {
        return new EmailJobSubscriptionInterceptor(properties, () -> authz);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Message<byte[]> subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination) {
        return subscribe(destination, null);
    }

    private Authentication manager() {
        return new UsernamePasswordAuthenticationToken("ops", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_EVENT_MANAGER")));
    }

    // --- destination shape: unconditional, no principal involved ---------------------------------

    @Test
    void rejectsWildcardAcrossTheEmailJobNamespace() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/*"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/**"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/1b4e28ba-*"), null));
    }

    @Test
    void rejectsWildcardRootedAboveTheNamespace() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/**"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/**"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/{a}/{b}"), null));
    }

    /** A relay-backed host may be speaking a broker whose multi-level wildcard is {@code #}. */
    @Test
    void rejectsRabbitStyleWildcardReachingTheFeed() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/#"), null));
    }

    @Test
    void rejectsNonUuidJobIdAndNestedDestinations() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/not-a-uuid"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/1-1-1-1-1"), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe(JOB + "/detail"), null));
    }

    /**
     * The shape check needs no principal, so it must survive the escape hatch that switches the
     * principal check off.
     */
    @Test
    void shapeCheckAppliesEvenWhenPrincipalCheckIsDisabled() {
        properties.getWebsocket().setSubscriptionAuthorization(SubscriptionAuthorization.DISABLED);

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/topic/email-jobs/*"), null));
        verify(authz, never()).canManageEvents();
    }

    @Test
    void honoursAConfiguredTopicPrefix() {
        properties.getWebsocket().setTopicPrefix("/queue/");

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe("/queue/email-jobs/*"), null));

        Message<byte[]> concrete = subscribe("/queue/email-jobs/" + UUID.randomUUID());
        assertSame(concrete, interceptor().preSend(concrete, null));

        // The old prefix is no longer core's namespace, so it is no longer core's to police.
        Message<byte[]> stale = subscribe("/topic/email-jobs/*");
        assertSame(stale, interceptor().preSend(stale, null));
    }

    // --- the host's own traffic must pass through untouched --------------------------------------

    @Test
    void leavesDestinationsOutsideTheFeedAlone() {
        for (String destination : List.of("/topic/orders/*", "/topic/host-thing", "/user/queue/errors",
                "/topic/email-jobs", "/topic/scans/**")) {
            Message<byte[]> message = subscribe(destination);
            assertSame(message, interceptor().preSend(message, null), destination);
        }
        verify(authz, never()).canManageEvents();
    }

    @Test
    void leavesNonSubscribeFramesAlone() {
        for (StompCommand command : List.of(StompCommand.CONNECT, StompCommand.SEND,
                StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT)) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
            accessor.setDestination("/topic/email-jobs/*");
            Message<byte[]> message =
                    MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertSame(message, interceptor().preSend(message, null), command.name());
        }
    }

    // --- principal check --------------------------------------------------------------------------

    @Test
    void allowsAConcreteDestinationForAnEventManager() {
        when(authz.canManageEvents()).thenReturn(true);

        Message<byte[]> message = subscribe(JOB, manager());

        assertSame(message, interceptor().preSend(message, null));
    }

    @Test
    void rejectsAConcreteDestinationForAnAuthenticatedNonManager() {
        when(authz.canManageEvents()).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe(JOB, manager()), null));
    }

    @Test
    void rejectsAnonymousSession() {
        when(authz.canManageEvents()).thenReturn(false);
        Principal anonymous = new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe(JOB, anonymous), null));
    }

    /**
     * The inbound channel is a thread pool, so core's authorizers see an empty
     * {@code SecurityContextHolder} unless the interceptor installs the session's identity itself.
     */
    @Test
    void installsTheSessionAuthenticationForTheGatewayCheck() {
        Authentication caller = manager();
        when(authz.canManageEvents())
                .thenAnswer(invocation ->
                        SecurityContextHolder.getContext().getAuthentication() == caller);

        Message<byte[]> message = subscribe(JOB, caller);

        assertSame(message, interceptor().preSend(message, null));
    }

    /** A stale context left on a pooled thread would be worse than the bug being fixed. */
    @Test
    void restoresAnEmptySecurityContextAfterwards() {
        when(authz.canManageEvents()).thenReturn(true);

        interceptor().preSend(subscribe(JOB, manager()), null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void restoresAPrePopulatedSecurityContextAfterwards() {
        Authentication existing = manager();
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(authz.canManageEvents()).thenReturn(true);

        interceptor().preSend(subscribe(JOB, null), null);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * A principal core cannot turn into an {@link Authentication} still means the session is
     * authenticated, so the check runs — against an empty context, and therefore fails closed.
     */
    @Test
    void failsClosedForAPrincipalThatIsNotAnAuthentication() {
        when(authz.canManageEvents()).thenReturn(false);
        Principal opaque = () -> "someone";

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe(JOB, opaque), null));
    }

    // --- the standalone-app carve-out ------------------------------------------------------------

    /**
     * AUTO, the default: nothing authenticated the session, so there is no identity to judge and the
     * gate is skipped. This is what keeps the standalone {@code tcketmanage-app} — which enables no
     * security at all, and where {@code RoleBasedAuthorizer} would therefore deny everyone — able to
     * follow its own bulk-email progress.
     */
    @Test
    void autoSkipsTheGateWhenTheSessionCarriesNoPrincipal() {
        Message<byte[]> message = subscribe(JOB, null);

        assertSame(message, interceptor().preSend(message, null));
        verify(authz, never()).canManageEvents();
    }

    @Test
    void requiredRejectsASessionCarryingNoPrincipal() {
        properties.getWebsocket().setSubscriptionAuthorization(SubscriptionAuthorization.REQUIRED);

        assertThrows(AccessDeniedException.class,
                () -> interceptor().preSend(subscribe(JOB, null), null));
        verify(authz, never()).canManageEvents();
    }

    @Test
    void requiredStillAdmitsAnEventManager() {
        properties.getWebsocket().setSubscriptionAuthorization(SubscriptionAuthorization.REQUIRED);
        when(authz.canManageEvents()).thenReturn(true);

        Message<byte[]> message = subscribe(JOB, manager());

        assertSame(message, interceptor().preSend(message, null));
    }

    @Test
    void disabledSkipsTheGateEvenForAnAuthenticatedNonManager() {
        properties.getWebsocket().setSubscriptionAuthorization(SubscriptionAuthorization.DISABLED);

        Message<byte[]> message = subscribe(JOB, manager());

        assertSame(message, interceptor().preSend(message, null));
        verify(authz, never()).canManageEvents();
    }

    // --- defaults --------------------------------------------------------------------------------

    @Test
    void defaultsToAutoAndToNoWildcardCorsOrigin() {
        assertEquals(SubscriptionAuthorization.AUTO,
                new TcketManageProperties().getWebsocket().getSubscriptionAuthorization());
        // SECURITY: the wildcard default is gone; a deployment names its origins explicitly.
        assertEquals(List.of(), new TcketManageProperties().getCors().getAllowedOrigins());
    }
}
