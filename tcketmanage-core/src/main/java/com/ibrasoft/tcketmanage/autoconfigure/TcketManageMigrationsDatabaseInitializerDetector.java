package com.ibrasoft.tcketmanage.autoconfigure;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

import java.util.Set;

/**
 * Tells Spring Boot that {@link TcketManageCoreMigrations} is a database initializer, so anything
 * annotated {@code @DependsOnDatabaseInitialization} — most importantly {@code entityManagerFactory}
 * — is made to wait for core's migrations before it touches the schema. Without this, Hibernate's
 * {@code ddl-auto=validate} can run before core's tables exist and fail startup.
 *
 * <p>This is the supported replacement for the ordering core would otherwise have inherited from
 * Boot's {@code flywayInitializer}; see {@link TcketManageCoreMigrations} for why core cannot simply
 * contribute a {@code Flyway} bean and rely on that.
 *
 * <p>Registered via {@code META-INF/spring.factories} under
 * {@code org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector}. Detection is by
 * bean type, mirroring Boot's own {@code FlywayMigrationInitializerDatabaseInitializerDetector}.
 */
class TcketManageMigrationsDatabaseInitializerDetector
        extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(TcketManageCoreMigrations.class);
    }
}
