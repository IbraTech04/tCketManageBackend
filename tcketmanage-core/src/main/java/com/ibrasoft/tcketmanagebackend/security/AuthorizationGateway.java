package com.ibrasoft.tcketmanagebackend.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;

/**
 * The single bean core's {@code @PreAuthorize} expressions talk to, registered under the fixed name
 * {@code tcketmanageAuthz}.
 *
 * <p>It exists so that a host can override authorization without having to guess a bean name.
 * Referencing the SPI directly from an annotation ({@code @tcketmanageAuthz.canScan()}) would tie
 * every expression in core to whatever name the overriding bean happened to be given; if a host
 * declared its {@link TcketManageAuthorizer} as, say, {@code lensBridgeTicketAuthorizer}, the
 * expressions would fail to resolve at request time — a 500 on the first scan rather than an error
 * at startup.
 *
 * <p>This gateway deliberately does <strong>not</strong> implement {@link TcketManageAuthorizer}.
 * If it did, the {@link ObjectProvider} below would find the gateway itself and the bean would
 * depend on itself. Being a separate type, it can safely ask for "the host's authorizer, if there
 * is one" and fall back to {@link RoleBasedAuthorizer} when there is not.
 */
public class AuthorizationGateway {

    private final TcketManageAuthorizer delegate;

    /**
     * @param hierarchies the host's {@link RoleHierarchy}, if it declares one. Only consulted when
     *                    falling back to {@link RoleBasedAuthorizer} — a host that supplies its own
     *                    authorizer decides for itself what role inheritance means.
     */
    public AuthorizationGateway(ObjectProvider<TcketManageAuthorizer> provided,
                                SecurityRoleProperties roles,
                                ObjectProvider<RoleHierarchy> hierarchies) {
        this.delegate = provided.getIfAvailable(
                () -> new RoleBasedAuthorizer(roles, hierarchies.getIfAvailable()));
    }

    /** The authorizer in force — the host's if it supplied one, otherwise core's role-based default. */
    public TcketManageAuthorizer delegate() {
        return delegate;
    }

    public boolean canScan() {
        return delegate.canScan();
    }

    public boolean canManageEvents() {
        return delegate.canManageEvents();
    }

    public boolean canAdminister() {
        return delegate.canAdminister();
    }

    public boolean isOperator() {
        return delegate.isOperator();
    }
}
