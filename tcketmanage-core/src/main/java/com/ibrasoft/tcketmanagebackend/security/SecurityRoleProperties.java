package com.ibrasoft.tcketmanagebackend.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    private String prefix = "ROLE_";
}
