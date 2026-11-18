package com.example.microservicios.inventario.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Declares the authoritative pool of the service.
 *
 * <p>W2 runs against a single database. The bean is named {@code primaryDataSource} from the start
 * because two consumers need to name it explicitly later: Flyway (which must never be pointed at a
 * router or a replica, ADR-06) and the routing configuration of W6, which builds the replica pools
 * around it.
 *
 * <p>Pool settings are bound from {@code spring.datasource.hikari.*}; the JDBC coordinates come
 * from {@code spring.datasource.url|username|password}, which in turn only read environment
 * variables.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource primaryDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
