package com.ibrasoft.tcketmanage.autoconfigure;

import com.ibrasoft.tcketmanagebackend.security.AuthorizationGateway;
import com.ibrasoft.tcketmanagebackend.security.SecurityRoleProperties;
import com.ibrasoft.tcketmanagebackend.security.TcketManageAuthorizer;
import com.ibrasoft.tcketmanagebackend.service.order.OrderOwnerResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;

import javax.sql.DataSource;

/**
 * Self-wiring entry point for the tCketManage core library.
 *
 * <p>Core is a plain library jar with no {@code @SpringBootApplication}, so it cannot rely on a
 * host's component scan. Instead this auto-configuration discovers and registers every core bean itself:
 * <ul>
 *   <li>{@link ComponentScan} over the core base package picks up its {@code @Component} beans,
 *       including the {@code @Configuration} classes and {@code @ConfigurationProperties} POJOs;</li>
 *   <li>{@link CorePackageRegistrar} adds core's base package to Spring Boot's
 *       {@link AutoConfigurationPackages} so that Boot's own (un-suppressed) JPA auto-configuration
 *       scans core's entities and repositories <em>in addition to</em> the host's. We deliberately do
 *       <strong>not</strong> use {@code @EntityScan}/{@code @EnableJpaRepositories} here: either of
 *       those makes Boot back off from scanning the host's auto-configuration package, which would
 *       leave a host application (e.g. LensBridge) unable to find its own entities/repositories;</li>
 * </ul>
 *
 * <p>Note what is <em>not</em> here: core does not enable Spring's annotation-driven scheduling.
 * Doing so would turn it on for the entire host application and, because Boot's default scheduler is
 * single-threaded, would put core's periodic work on the same thread as the host's. Core's only
 * recurring job runs on a private executor instead — see
 * {@link com.ibrasoft.tcketmanagebackend.service.order.OrderExpiryScheduler}.
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
// Fully-qualified bean names so core's scanned @Component/@Configuration beans (e.g. WebConfig,
// WebSocketConfig, GlobalExceptionHandler) never collide with identically-named beans in a host
// application's own component scan. Only affects scanned beans; explicit @Bean(name=...) is unchanged.
@ComponentScan(basePackages = "com.ibrasoft.tcketmanagebackend",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import({TcketManageAutoConfiguration.CorePackageRegistrar.class, WebSocketBrokerFallbackConfig.class})
@EnableConfigurationProperties({TcketManageFlywayProperties.class, TcketManageProperties.class})
public class TcketManageAutoConfiguration {

    static final String CORE_BASE_PACKAGE = "com.ibrasoft.tcketmanagebackend";

    /**
     * Appends core's base package to Boot's {@link AutoConfigurationPackages} so the default JPA
     * auto-configuration scans core's entities/repositories alongside the host's, without the host
     * needing any tCketManage-specific {@code @EntityScan}/{@code @EnableJpaRepositories}.
     */
    static class CorePackageRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            AutoConfigurationPackages.register(registry, CORE_BASE_PACKAGE);
        }
    }

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

    /**
     * Fail-fast at startup if core's STOMP destination is not one the application's broker serves.
     * See {@link WebSocketDestinationRequirement}.
     */
    @Bean
    WebSocketDestinationRequirement tcketManageWebSocketDestinationRequirement(
            ObjectProvider<AbstractBrokerMessageHandler> brokers, TcketManageProperties properties) {
        return new WebSocketDestinationRequirement(brokers, properties);
    }

    /**
     * Default {@link OrderOwnerResolver}: tags every order as anonymous/guest ({@code null} owner ref),
     * preserving core's guest-checkout behavior. An embedding host with its own accounts overrides this
     * simply by declaring its own {@code OrderOwnerResolver} bean (which wins via
     * {@link ConditionalOnMissingBean}).
     */
    @Bean
    @ConditionalOnMissingBean
    OrderOwnerResolver tcketManageDefaultOrderOwnerResolver() {
        return () -> null;
    }

    /**
     * Runs core's own schema migrations against the host's {@link DataSource}, under a migration
     * history separate from any Flyway the host already manages.
     *
     * <p>Note the bean type: contributing a {@code Flyway} (or a {@code FlywayMigrationInitializer})
     * here would switch off Spring Boot's Flyway auto-configuration and take the host's own
     * migrations down with it. {@link TcketManageCoreMigrations} documents that trap in full.
     */
    @Bean
    TcketManageCoreMigrations tcketManageCoreMigrations(DataSource dataSource,
                                                       TcketManageFlywayProperties flywayProperties) {
        return new TcketManageCoreMigrations(dataSource, flywayProperties);
    }

    /**
     * Publishes the URL patterns a host needs to secure core; see {@link TcketManagePaths}.
     */
    @Bean
    TcketManagePaths tcketManagePaths(TcketManageProperties properties) {
        return new TcketManagePaths(properties.getBasePath());
    }

    /**
     * The bean core's {@code @PreAuthorize} expressions resolve as {@code @tcketmanageAuthz}.
     *
     * <p>Registered unconditionally under a fixed name; a host overrides authorization by declaring
     * a {@link TcketManageAuthorizer} bean under any name, which this gateway picks up by type. See
     * {@link AuthorizationGateway} for why the indirection is needed.
     */
    @Bean("tcketmanageAuthz")
    AuthorizationGateway tcketManageAuthorizationGateway(ObjectProvider<TcketManageAuthorizer> authorizers,
                                                        SecurityRoleProperties roles,
                                                        ObjectProvider<RoleHierarchy> roleHierarchies) {
        return new AuthorizationGateway(authorizers, roles, roleHierarchies);
    }

    /**
     * Orders the host's Flyway migrations after core's, so a host migration may reference core's
     * tables. See {@link FlywayDependsOnCoreMigrationsPostProcessor}.
     *
     * <p>Static because a {@code BeanFactoryPostProcessor} must be instantiated before the rest of
     * this configuration class.
     */
    @Bean
    @ConditionalOnProperty(prefix = "tcketmanage.flyway", name = "run-first", matchIfMissing = true)
    static FlywayDependsOnCoreMigrationsPostProcessor tcketManageFlywayDependsOnCoreMigrations() {
        return new FlywayDependsOnCoreMigrationsPostProcessor();
    }
}
