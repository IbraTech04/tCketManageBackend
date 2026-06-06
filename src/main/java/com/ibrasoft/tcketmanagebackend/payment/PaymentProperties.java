package com.ibrasoft.tcketmanagebackend.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment-level payment configuration, bound from {@code payments.*}.
 */
@Component
@ConfigurationProperties(prefix = "payments")
@Data
public class PaymentProperties {

    /** Provider id used when an order request doesn't specify one. */
    private String defaultProvider = "mock";

    /**
     * Shared secret required (as the {@code X-Admin-Token} header) to call operator endpoints such
     * as manual payment confirmation. Interim measure until real authentication is added.
     */
    private String adminToken;

    private Mock mock = new Mock();
    private Interac interac = new Interac();

    @Data
    public static class Mock {
        private boolean enabled = true;
        /** When true, {@code initiate} auto-confirms (Completed); when false, returns Instructions. */
        private boolean autoConfirm = true;
        private long holdMinutes = 30;
    }

    @Data
    public static class Interac {
        private boolean enabled = false;
        private String payeeEmail;
        private long holdHours = 48;
    }
}
