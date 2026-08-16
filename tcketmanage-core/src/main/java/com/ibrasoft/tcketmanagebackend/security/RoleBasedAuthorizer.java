package com.ibrasoft.tcketmanagebackend.security;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * Core's default {@link TcketManageAuthorizer}: checks the caller's granted authorities against the
 * three role names in {@link SecurityRoleProperties}.
 *
 * <p>Used only when the host declares no {@code TcketManageAuthorizer} of its own, so core's
 * historical behaviour — {@code hasRole(@tcketmanageRoles.eventManager)} and friends, remappable via
 * {@code tcketmanage.security.roles.*} — is exactly what an existing deployment keeps.
 *
 * <p>Preserving that behaviour is the reason for the {@link RoleHierarchy} below. The
 * {@code hasRole(...)} expressions this class replaced were evaluated by Spring Security's
 * {@code SecurityExpressionRoot}, which expands the caller's authorities through the context's role
 * hierarchy before comparing. Comparing raw authorities instead would silently drop that expansion:
 * a host configured with {@code ROLE_ADMIN > ROLE_SCANNER} would find its admins locked out of the
 * scan endpoints, which is precisely the inheritance {@code ScanEventController} documents. So the
 * hierarchy is applied here too, when the host has one.
 *
 * <p>The authority prefix comes from {@link SecurityRoleProperties#getPrefix()} rather than being
 * hardcoded, for the analogous reason on the other side: {@code hasRole()} honours a host's
 * {@code GrantedAuthorityDefaults}, and core cannot read that bean because it lives in
 * {@code spring-security-config}. A host that has changed the prefix restates it in core's config.
 */
public class RoleBasedAuthorizer implements TcketManageAuthorizer {

    private final SecurityRoleProperties roles;
    private final RoleHierarchy hierarchy;

    public RoleBasedAuthorizer(SecurityRoleProperties roles) {
        this(roles, null);
    }

    /**
     * @param hierarchy the host's role hierarchy, or {@code null} when it declares none — in which
     *                  case authorities are compared exactly as granted.
     */
    public RoleBasedAuthorizer(SecurityRoleProperties roles, RoleHierarchy hierarchy) {
        this.roles = roles;
        this.hierarchy = hierarchy;
    }

    @Override
    public boolean canScan() {
        return hasRole(roles.getScanner());
    }

    @Override
    public boolean canManageEvents() {
        return hasRole(roles.getEventManager());
    }

    @Override
    public boolean canAdminister() {
        return hasRole(roles.getAdmin());
    }

    private boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String prefix = roles.getPrefix() == null ? "" : roles.getPrefix();
        String expected = role.startsWith(prefix) ? role : prefix + role;
        for (GrantedAuthority authority : reachable(authentication)) {
            if (expected.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private Collection<? extends GrantedAuthority> reachable(Authentication authentication) {
        if (hierarchy == null) {
            return authentication.getAuthorities();
        }
        return hierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());
    }
}
