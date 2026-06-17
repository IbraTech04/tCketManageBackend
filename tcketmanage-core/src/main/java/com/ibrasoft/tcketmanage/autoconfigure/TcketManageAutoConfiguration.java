package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Self-wiring entry point for the tCketManage core library.
 *
 * <p>Core is a plain library jar with no {@code @SpringBootApplication}, so it cannot rely on a
 * host's component scan. Instead this auto-configuration discovers and registers every core bean itself:
 * <ul>
 *   <li>{@link ComponentScan} over the core base package picks up its {@code @Component} beans,
 *       including the {@code @Configuration} classes and {@code @ConfigurationProperties} POJOs;</li>
 *   <li>{@link EntityScan} / {@link EnableJpaRepositories} register the JPA entities and
 *       repositories that live under core's packages (the host's own entity/repository scanning
 *       would otherwise miss them);</li>
 *   <li>{@link EnableScheduling} enables the order-expiry sweep and other scheduled work.</li>
 * </ul>
 *
 * <p>It lives in {@code com.ibrasoft.tcketmanage.autoconfigure} (deliberately outside the scanned
 * {@code com.ibrasoft.tcketmanagebackend} base package) so the component scan does not re-discover
 * this class.
 *
 * <p>Gated on {@code tcketmanage.enabled=true} (off by default) so a host opts in explicitly. The
 * standalone {@code tcketmanage-app} sets it in its {@code application.properties}. Registered for
 * auto-configuration via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "tcketmanage", name = "enabled", matchIfMissing = false)
@ComponentScan("com.ibrasoft.tcketmanagebackend")
@EntityScan("com.ibrasoft.tcketmanagebackend.model")
@EnableJpaRepositories("com.ibrasoft.tcketmanagebackend.repository")
@EnableScheduling
public class TcketManageAutoConfiguration {

    /**
     * Fail-fast at startup if the host disabled {@code spring.jpa.open-in-view}, which core's read
     * endpoints depend on. See {@link OpenInViewRequirement}.
     * 
     * Eventually, {@code OpenInViewRequirement} and this bean should be removed once the OSIV refactor lands and
     * core no longer depends on open-in-view. For now, this ensures that a misconfiguration is caught at boot
     * rather than failing silently at request time with 500s.
     */
    @Bean
    OpenInViewRequirement tcketManageOpenInViewRequirement(Environment environment) {
        return new OpenInViewRequirement(environment);
    }
}
