package com.example.microservicios.inventario;

import com.example.microservicios.persistence.routing.config.HikariDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DataSourcePoolConfigTest {

    @Test
    @DisplayName("Creates Hikari pool with required timeouts and properties")
    void shouldCreatePoolWithRequiredProperties() {
        try (HikariDataSource ds = HikariDataSourceFactory.create(
                "test-primary-pool",
                "jdbc:postgresql://localhost:5432/inventario_db",
                "inventario_user",
                "secret",
                5,
                "servicio-inventario:test-instance"
        )) {
            assertThat(ds.getConnectionTimeout()).isEqualTo(2000);
            assertThat(ds.getValidationTimeout()).isEqualTo(1000);
            assertThat(ds.getInitializationFailTimeout()).isEqualTo(-1);
            assertThat(ds.getMaximumPoolSize()).isEqualTo(5);
            assertThat(ds.getPoolName()).isEqualTo("test-primary-pool");
            assertThat(ds.getDataSourceProperties().getProperty("ApplicationName"))
                    .isEqualTo("servicio-inventario:test-instance");
        }
    }

    @Test
    @DisplayName("Does not fail application boot when a replica node is unreachable (initializationFailTimeout = -1)")
    void shouldNotFailBootWhenReplicaIsUnreachable() {
        // Pointing to a dead port must not throw on HikariDataSource initialization
        assertThatCode(() -> {
            try (HikariDataSource ds = HikariDataSourceFactory.create(
                    "dead-replica-pool",
                    "jdbc:postgresql://127.0.0.1:59999/inventario_db",
                    "inventario_user",
                    "secret",
                    5,
                    "servicio-inventario:local"
            )) {
                assertThat(ds.getInitializationFailTimeout()).isEqualTo(-1);
            }
        }).doesNotThrowAnyException();
    }
}
