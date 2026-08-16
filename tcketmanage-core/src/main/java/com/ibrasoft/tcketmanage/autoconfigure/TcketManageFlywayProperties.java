package com.ibrasoft.tcketmanage.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How core runs its own schema migrations, bound from {@code tcketmanage.flyway.*}.
 *
 * @see TcketManageCoreMigrations
 */
@ConfigurationProperties(prefix = "tcketmanage.flyway")
@Data
public class TcketManageFlywayProperties {

    /**
     * Whether core's migrations run before the host's own Flyway migrations. On by default so a
     * host migration may reference core's tables; see
     * {@link FlywayDependsOnCoreMigrationsPostProcessor}.
     *
     * <p>Read by {@code @ConditionalOnProperty} rather than from this object, and declared here so
     * it appears in the generated configuration metadata.
     */
    private boolean runFirst = true;

    /**
     * Version core's migration history is baselined at the first time it runs against a database
     * that has no {@code flyway_schema_history_tcketmanage} table.
     *
     * <p>The default {@code 0} sits below every real migration and means "the history table is
     * merely missing — apply everything". That is what you want when adopting core into an
     * application whose database already has tables, but none of core's.
     *
     * <p>Set this to {@code 1} when the database <em>already contains core's schema</em> from
     * before core shipped migrations — typically a deployment that ran under Hibernate's
     * {@code ddl-auto=update}. Baselining at {@code 1} records {@code V1} as already applied
     * instead of executing its {@code CREATE TABLE}s against tables that exist, which would fail.
     *
     * <p>Set it <em>before</em> the first boot against such a database. Flyway only consults a
     * baseline version while creating the history table, so once a failed startup has created
     * {@code flyway_schema_history_tcketmanage} baselined at {@code 0}, raising this changes
     * nothing and {@code V1} goes on failing with "table already exists". Recovering from that
     * means dropping the history table and starting again with the right value.
     */
    private String baselineVersion = "0";
}
