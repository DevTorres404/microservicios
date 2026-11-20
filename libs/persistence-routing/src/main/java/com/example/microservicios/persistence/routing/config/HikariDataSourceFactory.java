package com.example.microservicios.persistence.routing.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Factory for creating HikariDataSource instances for primary and replica PostgreSQL nodes.
 * Mandated settings:
 * - connectionTimeout: 2000 ms
 * - validationTimeout: 1000 ms
 * - initializationFailTimeout: -1 (allows bean creation and app startup even when a node is unreachable)
 * - ApplicationName: stamped with service instance identifier for pg_stat_activity observability
 */
public final class HikariDataSourceFactory {

    private HikariDataSourceFactory() {
    }

    public static HikariDataSource create(String poolName,
                                          String jdbcUrl,
                                          String username,
                                          String password,
                                          int maxPoolSize,
                                          String applicationName) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maxPoolSize > 0 ? maxPoolSize : 10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(2000);
        config.setValidationTimeout(1000);
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("ApplicationName", applicationName != null ? applicationName : poolName);

        return new HikariDataSource(config);
    }
}
