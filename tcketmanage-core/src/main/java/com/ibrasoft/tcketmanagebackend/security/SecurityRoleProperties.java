package com.ibrasoft.tcketmanagebackend.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The role names core's {@code @PreAuthorize} checks resolve to, bound from
 * {@code tcketmanage.security.roles.*}. Lets an embedding host map core's three logical roles onto
 * its <em>own</em> existing roles without editing core - e.g. a host with {@code USER/ADMIN/ROOT}
 * can set {@code tcketmanage.security.roles.event-manager=ADMIN} and
 * {@code tcketmanage.security.roles.admin=ROOT} instead of adding new roles.
 *
 * <p>Referenced from annotations as {@code @PreAuthorize("hasRole(@tcketmanageRoles.eventManager)")}
 * (the {@code @tcketmanageRoles} bean name). Values are role names <strong>without</strong> the
 * {@code ROLE_} prefix — {@code hasRole(...)} adds it, matching the host's existing {@code hasRole}
 * convention.
 *
 * <p>Defaults are core's native role names, so the standalone app and any host that adopts them need
 * no configuration. (Enforcement still requires the host to enable method security; standalone leaves
 * it off, so these are inert there.)
 */
@Component("tcketmanageRoles")
@ConfigurationProperties(prefix = "tcketmanage.security.roles")
@Data
public class SecurityRoleProperties {

    /** Role for scanning/validation/scan-history endpoints. */
    private String scanner = "SCANNER";

    /** Role for event setup, attendee roster, ticket issuance, delivery, order book, email jobs. */
    private String eventManager = "EVENT_MANAGER";

    /** Role for destructive deletes and manual payment settlement. */
    private String admin = "ADMIN";
}
