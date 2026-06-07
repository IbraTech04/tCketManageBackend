package com.ibrasoft.tcketmanagebackend.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment-level email configuration, bound from {@code app.email.*}.
 *
 * <p>When {@code app.email.enabled=true} the SMTP {@link SmtpEmailService} is wired in and the
 * usual {@code spring.mail.*} properties (host, port, username, password) must also be set;
 * otherwise the {@link LoggingEmailService} stub stays active.
 */
@Component
@ConfigurationProperties(prefix = "app.email")
@Data
public class EmailProperties {

    /** Master switch selecting the SMTP sender over the logging stub. */
    private boolean enabled = false;

    /** From address used on outgoing ticket emails. */
    private String from = "no-reply@tcketmanage.com";

    /** Display name shown alongside the from address. */
    private String fromName = "tCketManage";
}
