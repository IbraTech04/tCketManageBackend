package com.ibrasoft.tcketmanagebackend.properties;

import com.ibrasoft.tcketmanagebackend.service.email.LoggingEmailService;
import com.ibrasoft.tcketmanagebackend.service.email.SmtpEmailService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment-level email configuration, bound from {@code tcketmanage.email.*}.
 *
 * <p>When {@code tcketmanage.email.enabled=true} the SMTP {@link SmtpEmailService} is wired in and the
 * usual {@code spring.mail.*} properties (host, port, username, password) must also be set;
 * otherwise the {@link LoggingEmailService} stub stays active.
 */
@Component
@ConfigurationProperties(prefix = "tcketmanage.email")
@Data
public class EmailProperties {

    /** Master switch selecting the SMTP sender over the logging stub. */
    private boolean enabled = false;

    /** From address used on outgoing ticket emails. */
    private String from = "no-reply@tcketmanage.com";

    /** Display name shown alongside the from address. */
    private String fromName = "tCketManage";

    /** Async dispatch tuning, bound from {@code tcketmanage.email.async.*}. */
    private final Async async = new Async();

    /**
     * Controls the dedicated executor that ticket emails are sent on. Kept deliberately small:
     * SMTP is I/O-bound and most providers rate-limit, so a wide pool buys nothing and risks
     * tripping send limits.
     */
    @Data
    public static class Async {

        /** Worker threads (and max pool size) for the email executor. */
        private int concurrency = 3;

        /** How many pending sends may queue before the submitting thread runs them inline. */
        private int queueCapacity = 500;
    }
}
