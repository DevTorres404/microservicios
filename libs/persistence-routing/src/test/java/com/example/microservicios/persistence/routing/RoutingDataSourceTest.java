package com.example.microservicios.persistence.routing;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingDataSourceTest {

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("Resolves to primary when context key is null")
    void shouldResolveToPrimaryWhenKeyIsNull() {
        DataSource primary = Mockito.mock(DataSource.class);
        DataSource replica1 = Mockito.mock(DataSource.class);
        DataSource replica2 = Mockito.mock(DataSource.class);

        RoutingDataSource rds = new RoutingDataSource(primary, replica1, replica2);

        assertThat(rds.determineCurrentLookupKey()).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(rds.determineTargetDataSource()).isSameAs(primary);
    }

    @Test
    @DisplayName("Resolves to replica when context key is specified")
    void shouldResolveToReplicaWhenContextIsSet() {
        DataSource primary = Mockito.mock(DataSource.class);
        DataSource replica1 = Mockito.mock(DataSource.class);
        DataSource replica2 = Mockito.mock(DataSource.class);

        RoutingDataSource rds = new RoutingDataSource(primary, replica1, replica2);

        DataSourceContextHolder.push(DataSourceKey.REPLICA_1);
        assertThat(rds.determineCurrentLookupKey()).isEqualTo(DataSourceKey.REPLICA_1);
        assertThat(rds.determineTargetDataSource()).isSameAs(replica1);

        DataSourceContextHolder.push(DataSourceKey.REPLICA_2);
        assertThat(rds.determineCurrentLookupKey()).isEqualTo(DataSourceKey.REPLICA_2);
        assertThat(rds.determineTargetDataSource()).isSameAs(replica2);
    }

    @Test
    @DisplayName("Fails noisily with IllegalStateException when an unknown key is in context (lenientFallback=false)")
    void shouldFailNoisilyOnUnknownKey() {
        DataSource primary = Mockito.mock(DataSource.class);
        // Only primary and replica1 in map, replica2 is missing
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceKey.PRIMARY, primary);

        RoutingDataSource rds = new RoutingDataSource(primary, targetDataSources);

        DataSourceContextHolder.push(DataSourceKey.REPLICA_2);

        assertThatThrownBy(rds::determineTargetDataSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot determine target DataSource for lookup key");
    }

    @Test
    @DisplayName("Throws DbNodeUnavailableException and immediately demotes replica when pool fails to provide connection")
    void shouldDemoteReplicaAndThrowDbNodeUnavailableOnConnectionFailure() throws Exception {
        DataSource primary = Mockito.mock(DataSource.class);
        DataSource replica1 = Mockito.mock(DataSource.class);
        DataSource replica2 = Mockito.mock(DataSource.class);
        ReplicaHealthRegistry healthRegistry = Mockito.mock(ReplicaHealthRegistry.class);

        Mockito.when(replica1.getConnection()).thenThrow(new SQLException("Connection refused"));

        RoutingDataSource rds = new RoutingDataSource(primary, replica1, replica2, healthRegistry);

        DataSourceContextHolder.push(DataSourceKey.REPLICA_1);

        assertThatThrownBy(rds::getConnection)
                .isInstanceOf(DbNodeUnavailableException.class)
                .hasMessageContaining("REPLICA_1");

        Mockito.verify(healthRegistry).markDown(DataSourceKey.REPLICA_1, "connection-refused");
    }

    @Test
    @DisplayName("Throws DbNodeUnavailableException when primary pool fails to provide connection")
    void shouldThrowDbNodeUnavailableOnPrimaryConnectionFailure() throws Exception {
        DataSource primary = Mockito.mock(DataSource.class);
        DataSource replica1 = Mockito.mock(DataSource.class);
        DataSource replica2 = Mockito.mock(DataSource.class);
        ReplicaHealthRegistry healthRegistry = Mockito.mock(ReplicaHealthRegistry.class);

        Mockito.when(primary.getConnection()).thenThrow(new SQLException("Primary connection pool exhausted"));

        RoutingDataSource rds = new RoutingDataSource(primary, replica1, replica2, healthRegistry);

        DataSourceContextHolder.push(DataSourceKey.PRIMARY);

        assertThatThrownBy(rds::getConnection)
                .isInstanceOf(DbNodeUnavailableException.class)
                .hasMessageContaining("PRIMARY");

        Mockito.verifyNoInteractions(healthRegistry);
    }
}
