package com.ibrasoft.tcketmanagebackend.security;

import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Interim authorization for operator-only endpoints (CSV import, manual payment confirmation).
 * Validates the {@code X-Admin-Token} header against the configured shared secret. This is the seam
 * that real authentication will replace.
 */
@Component
@AllArgsConstructor
public class AdminGuard {

    private final PaymentProperties properties;

    /**
     * @throws SecurityException (→ 403) if the supplied token is missing or doesn't match.
     */
    public void require(String token) {
        String expected = properties.getAdminToken();
        if (expected == null || expected.isBlank() || !expected.equals(token)) {
            throw new SecurityException("Invalid or missing admin token");
        }
    }
}
