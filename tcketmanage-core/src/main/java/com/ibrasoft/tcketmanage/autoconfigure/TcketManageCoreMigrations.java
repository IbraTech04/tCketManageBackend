package com.ibrasoft.tcketmanage.autoconfigure;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.DatabaseDriver;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs core's own schema migrations against the host's {@link DataSource}, under a migration history
 * entirely separate from any Flyway the host already manages.
 *
 * <h2>Why this is not a {@code Flyway} bean</h2>
 * The obvious implementation — a {@code @Bean Flyway} that calls {@code migrate()} — silently breaks
 * an embedding host's own migrations, because Spring Boot's
 * {@code FlywayAutoConfiguration.FlywayConfiguration} carries a <em>class-level</em>
 * {@code @ConditionalOnMissingBean(Flyway.class)}. The moment core contributes any bean of type
 * {@link Flyway}, that entire inner configuration backs off — so Boot builds neither its own
 * {@code Flyway} nor, more importantly, its {@code flywayInitializer}. That initializer is the only
 * thing {@code FlywayMigrationInitializerDatabaseInitializerDetector} looks for when it makes
 * {@code entityManagerFactory} wait for migrations, and it matches on the
 * {@code FlywayMigrationInitializer} <em>type</em>. Lose it and nothing orders the host's migrations
 * against Hibernate's schema validation at all.
 *
 * <p>Registering a {@code FlywayMigrationInitializer} instead is no better: Boot's
 * {@code flywayInitializer} method has its own {@code @ConditionalOnMissingBean}, so core would
 * suppress it just the same and the host would end up with a {@code Flyway} bean nobody ever runs.
 *
 * <p>Hence this deliberately obscure shape: core's migrations are carried by a type Boot is not
 * looking for, holding a {@link Flyway} instance it never exposes as a bean. Boot's Flyway
 * auto-configuration is left completely untouched, and the host needs no compensating configuration.
 * Ordering is restored the supported way, via
 * {@link TcketManageMigrationsDatabaseInitializerDetector} (registered in {@code spring.factories}),
 * which tells Boot this bean is a database initializer so {@code entityManagerFactory} waits for it.
 *
 * <h2>Why a separate history table</h2>
 * A host typically already has its own migration history under {@code classpath:db/migration} with
 * its own version sequence — very often including a {@code V1}. Sharing Boot's Flyway would merge
 * core's versions into that ordered history and collide outright. Using a distinct location and the
 * {@value #HISTORY_TABLE} history table lets core's migrations be versioned from {@code V1} on their
 * own, independently of whatever the host has applied.
 *
 * <h2>Baselining</h2>
 * {@code baselineOnMigrate} is essential rather than incidental. Core is normally adopted into an
 * application that already has a populated database, and Flyway refuses to migrate a non-empty
 * schema whose history table is absent. Baselining at version {@code 0} — below every real migration
 * — says "the history table is merely missing, don't skip anything", rather than "assume some
 * migrations already ran". It is a no-op when the schema really is empty, since baselining only
 * fires when the history table is absent AND the schema is not. A database that already carries
 * core's own tables from before core shipped migrations needs
 * {@code tcketmanage.flyway.baseline-version=1} instead; see
 * {@link TcketManageFlywayProperties#getBaselineVersion()}.
 *
 * @see TcketManageAutoConfiguration#tcketManageCoreMigrations
 */
public class TcketManageCoreMigrations implements InitializingBean {

    /** Core's private migration history, kept apart from the host's {@code flyway_schema_history}. */
    static final String HISTORY_TABLE = "flyway_schema_history_tcketmanage";

    /**
     * Postgres-flavored migrations ({@code uuid}, {@code timestamptz}, quoted {@code "tcket:*"}
     * identifiers), used for every database except SQLite since that is what real deployments run.
     *
     * <p>Deliberately <strong>outside</strong> {@code db/migration}. Flyway scans its locations
     * recursively, and {@code classpath:db/migration} is Flyway's default — so migrations kept in a
     * subdirectory of it would be swept up by any host that has not overridden
     * {@code spring.flyway.locations}, merging core's versions into the host's history and failing
     * outright on the duplicate {@code V1} both sides are likely to have.
     */
    static final String DEFAULT_LOCATION = "classpath:db/tcketmanage/postgresql";

    /**
     * SQLite twin of {@link #DEFAULT_LOCATION}, translated the same way a host translates its own
     * migrations for SQLite: {@code uuid -> blob}, {@code timestamptz -> timestamp}.
     */
    static final String SQLITE_LOCATION = "classpath:db/tcketmanage/sqlite";

    private final DataSource dataSource;
    private final TcketManageFlywayProperties properties;

    TcketManageCoreMigrations(DataSource dataSource, TcketManageFlywayProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws SQLException {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(resolveLocation())
                .table(HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion(properties.getBaselineVersion())
                .load()
                .migrate();
    }

    /**
     * Picks the migration flavor from a live connection's JDBC URL rather than from a host property,
     * so this stays correct regardless of how a given host names or layers its profiles.
     */
    private String resolveLocation() throws SQLException {
        String jdbcUrl;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        }
        return DatabaseDriver.fromJdbcUrl(jdbcUrl) == DatabaseDriver.SQLITE
                ? SQLITE_LOCATION
                : DEFAULT_LOCATION;
    }
}
