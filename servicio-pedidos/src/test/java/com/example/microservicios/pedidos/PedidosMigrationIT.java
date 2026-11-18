package com.example.microservicios.pedidos;

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
 * Verifies that the service owns a Flyway-managed order schema on its primary pool.
 */
@SpringBootTest
class PedidosMigrationIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesTheOrderSchemaThroughThePrimaryDataSource() {
        assertThat(flyway.getConfiguration().getDataSource())
                .as("Flyway must migrate the primary pool, never a routing or replica pool (ADR-06)")
                .isSameAs(primaryDataSource);

        assertThat(tableNames()).contains("orders", "order_lines", "flyway_schema_history");
        assertThat(appliedScripts()).containsExactly("V1__pedidos_schema.sql");
    }

    @Test
    void keepsOrderSchemaFreeOfInventoryTables() {
        assertThat(tableNames())
                .as("orders must not model stock: inventario_db is owned by servicio-inventario")
                .doesNotContain("stock", "reservations", "reservation_lines");
    }

    @Test
    void enforcesOrderStateAndLineGuards() {
        assertThat(constraintNames("c"))
                .contains("ck_orders_status", "ck_orders_total_non_negative")
                .contains("ck_order_lines_quantity_positive", "ck_order_lines_unit_price_non_negative");

        assertThat(constraintNames("f")).contains("fk_order_lines_order");

        assertThat(indexNames()).contains("idx_orders_status_created_at", "idx_order_lines_order");
    }

    @Test
    void rejectsStatusOutsideTheAllowedVocabulary() {
        assertThatThrownBy(() -> jdbc().update(
                        "insert into orders (customer, created_at, status, total)"
                                + " values ('ACME', now(), 'ENVIADO', 0.00)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_orders_status");
    }

    @Test
    void rejectsOrderLineWithoutAnExistingOrder() {
        assertThatThrownBy(() -> jdbc().update(
                        "insert into order_lines (order_id, sku, quantity) values (987654, 'SKU-ORPHAN', 1)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_order_lines_order");
    }

    @Test
    void reRunningTheMigrationIsIdempotent() {
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(appliedScripts()).containsExactly("V1__pedidos_schema.sql");
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
