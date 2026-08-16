package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;

/**
 * Makes the host's own Flyway migrations run <em>after</em> core's.
 *
 * <p>Core's tables are the ones a host builds on top of, so a host migration may legitimately
 * reference them — LensBridge's {@code V5__link_board_events_to_tcketmanage.sql}, for instance, adds
 * a foreign key to {@code "tcket:events"}. Since core's migrations live in a separate history
 * ({@link TcketManageCoreMigrations}), nothing would otherwise order the two, and the host's
 * migration would fail against a table that does not exist yet.
 *
 * <p>Being a database initializer only guarantees each runs before {@code entityManagerFactory}; it
 * says nothing about their order relative to each other. This adds the missing edge by declaring
 * every {@link FlywayMigrationInitializer} bean dependent on {@link TcketManageCoreMigrations}.
 * A host with no Flyway of its own has no such bean and this is a no-op.
 *
 * <p>Because core then creates its tables first, a host whose database is empty on its very first
 * boot will find a non-empty schema by the time its own Flyway looks at it, and Flyway refuses to
 * migrate an unrecognized non-empty schema. Such hosts should set
 * {@code spring.flyway.baseline-on-migrate=true}. Hosts adopting core into an already-populated
 * database — the usual case — are unaffected, as they need that setting regardless.
 *
 * <p>Disable with {@code tcketmanage.flyway.run-first=false} if a host would rather order the two
 * itself.
 */
class FlywayDependsOnCoreMigrationsPostProcessor extends AbstractDependsOnBeanFactoryPostProcessor {

    FlywayDependsOnCoreMigrationsPostProcessor() {
        super(FlywayMigrationInitializer.class, TcketManageCoreMigrations.class);
    }
}
