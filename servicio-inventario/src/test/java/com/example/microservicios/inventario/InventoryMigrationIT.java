package com.example.microservicios.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that the service owns a Flyway-managed inventory schema on its primary pool.
 *
 * <p>These assertions are intentionally made against PostgreSQL metadata, not against the Java
 * model: the schema is the artefact that must be correct.
 */
@SpringBootTest
class InventoryMigrationIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesTheInventorySchemaThroughThePrimaryDataSource() {
        assertThat(flyway.getConfiguration().getDataSource())
                .as("Flyway must migrate the primary pool, never a routing or replica pool (ADR-06)")
                .isSameAs(primaryDataSource);

        assertThat(tableNames())
                .contains("stock", "reservations", "reservation_lines", "flyway_schema_history");

        assertThat(appliedScripts()).containsExactly("V1__inventario_schema.sql");

        assertThat(jdbc().queryForObject(
                        "select success from flyway_schema_history where version = '1'", Boolean.class))
                .isTrue();
    }

    @Test
    void keepsInventorySchemaFreeOfOrderTables() {
        assertThat(tableNames())
                .as("inventory must not model order data: pedidos_db is owned by servicio-pedidos")
                .doesNotContain("orders", "order_lines");
    }

    @Test
    void enforcesReservationUniquenessAndStockGuards() {
        assertThat(constraintNames("u"))
                .contains("uq_reservations_order", "uq_reservation_lines_order_sku");

        assertThat(constraintNames("c"))
                .contains("ck_stock_available_non_negative", "ck_stock_unit_price_non_negative")
                .contains("ck_reservations_status", "ck_reservation_lines_quantity_positive");

        assertThat(indexNames()).contains("idx_reservation_lines_reservation");
    }

    @Test
    void rejectsNegativeAvailableStock() {
        assertThatThrownBy(() -> jdbc().update(
                        "insert into stock (sku, available, unit_price) values ('SKU-NEGATIVE', -1, 10.00)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_stock_available_non_negative");
    }

    @Test
    void rejectsUnknownReservationStatus() {
        assertThatThrownBy(() -> jdbc().update(
                        "insert into reservations (order_id, status) values (900001, 'PARCIAL')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_reservations_status");
    }

    @Test
    void reRunningTheMigrationIsIdempotent() {
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(appliedScripts()).containsExactly("V1__inventario_schema.sql");
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private List<String> tableNames() {
        return jdbc().queryForList(
                "select table_name from information_schema.tables"
                        + " where table_schema = 'public' and table_type = 'BASE TABLE'"
                        + " order by table_name",
                String.class);
    }

    private List<String> appliedScripts() {
        return jdbc().queryForList(
                "select script from flyway_schema_history where success order by installed_rank", String.class);
    }

    private List<String> constraintNames(String type) {
        return jdbc().queryForList(
                "select conname from pg_constraint"
                        + " where contype = ? and connamespace = 'public'::regnamespace order by conname",
                String.class,
                type);
    }

    private List<String> indexNames() {
        return jdbc().queryForList(
                "select indexname from pg_indexes where schemaname = 'public' order by indexname", String.class);
    }
}
