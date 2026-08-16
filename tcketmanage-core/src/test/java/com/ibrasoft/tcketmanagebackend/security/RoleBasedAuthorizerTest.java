package com.ibrasoft.tcketmanagebackend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the behaviours core's {@code hasRole(...)} expressions used to get from Spring Security's
 * {@code SecurityExpressionRoot} for free, and which {@link RoleBasedAuthorizer} has to reproduce
 * now that it compares authorities itself.
 */
class RoleBasedAuthorizerTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a",
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void matchesGrantedRoleWithDefaultPrefix() {
        authenticateWith("ROLE_SCANNER");

        RoleBasedAuthorizer authorizer = new RoleBasedAuthorizer(new SecurityRoleProperties());

        assertTrue(authorizer.canScan());
        assertFalse(authorizer.canManageEvents());
        assertFalse(authorizer.canAdminister());
    }

    @Test
    void deniesWhenNoAuthenticationPresent() {
        assertFalse(new RoleBasedAuthorizer(new SecurityRoleProperties()).canScan());
    }

    @Test
    void deniesAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertFalse(new RoleBasedAuthorizer(new SecurityRoleProperties()).canScan());
    }

    /**
     * The regression this class exists for: a host declaring {@code ROLE_ADMIN > ROLE_SCANNER}
     * expects its admins to reach the scan endpoints, which is what {@code ScanEventController}
     * documents. Comparing raw authorities would lock them out.
     */
    @Test
    void honoursHostRoleHierarchy() {
        authenticateWith("ROLE_ADMIN");
        RoleHierarchy hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_SCANNER");

        RoleBasedAuthorizer authorizer = new RoleBasedAuthorizer(new SecurityRoleProperties(), hierarchy);

        assertTrue(authorizer.canScan());
        assertTrue(authorizer.canAdminister());
    }

    @Test
    void withoutHierarchyRolesDoNotInherit() {
        authenticateWith("ROLE_ADMIN");

        RoleBasedAuthorizer authorizer = new RoleBasedAuthorizer(new SecurityRoleProperties());

        assertTrue(authorizer.canAdminister());
        assertFalse(authorizer.canScan());
    }

    @Test
    void honoursConfiguredAuthorityPrefix() {
        authenticateWith("SCANNER");
        SecurityRoleProperties roles = new SecurityRoleProperties();
        roles.setPrefix("");

        assertTrue(new RoleBasedAuthorizer(roles).canScan());
    }

    @Test
    void honoursRemappedRoleNames() {
        authenticateWith("ROLE_ROOT");
        SecurityRoleProperties roles = new SecurityRoleProperties();
        roles.setAdmin("ROOT");

        RoleBasedAuthorizer authorizer = new RoleBasedAuthorizer(roles);

        assertTrue(authorizer.canAdminister());
        assertTrue(authorizer.isOperator());
    }

    @Test
    void blankRoleNameDeniesRatherThanMatchingBarePrefix() {
        authenticateWith("ROLE_");
        SecurityRoleProperties roles = new SecurityRoleProperties();
        roles.setScanner("");

        assertFalse(new RoleBasedAuthorizer(roles).canScan());
    }
}
