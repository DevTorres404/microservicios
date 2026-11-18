package com.example.microservicios.pedidos.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates the order schema against the primary pool only.
 *
 * <p>Boot's Flyway auto-configuration is disabled in {@code application.yml}, so all three pieces
 * authored here are required: the {@link Flyway} instance, the initializer that actually calls
 * {@code migrate()}, and the bean dependency that orders migration before
 * {@code ddl-auto=validate}.
 */
@Configuration(proxyBeanMethods = false)
public class FlywayConfig {

    /** Location of this service's migrations. It contains no other service's scripts. */
    public static final String MIGRATION_LOCATION = "classpath:db/migration/pedidos";

    @Bean
    Flyway flyway(@Qualifier("primaryDataSource") DataSource primaryDataSource) {
        return Flyway.configure()
                .dataSource(primaryDataSource)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(false)
                // Tolerates a primary that is still starting in Compose without inventing a schema.
                .connectRetries(10)
                .load();
    }

    @Bean
    FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        return new FlywayMigrationInitializer(flyway);
    }

    /**
     * Hibernate validates the schema after the migration ran. Boot registers this ordering only
     * when its Flyway auto-configuration is active, and this service disables it on purpose.
     */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnFlyway() {
        return new EntityManagerFactoryDependsOnPostProcessor("flyway");
    }
}
