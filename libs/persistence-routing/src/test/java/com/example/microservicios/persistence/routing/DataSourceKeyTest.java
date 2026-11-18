package com.example.microservicios.persistence.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the closed vocabulary of routing keys.
 *
 * <p>Adding a fourth node must break this test on purpose: the routing map, the Hikari pools and the
 * evidence headers all assume exactly one primary and two replicas.</p>
 */
class DataSourceKeyTest {

    @Test
    void exposesExactlyTheThreeSupportedNodesInDeclaredOrder() {
        assertThat(DataSourceKey.values())
                .extracting(Enum::name)
                .containsExactly("PRIMARY", "REPLICA_1", "REPLICA_2");
    }

    @Test
    void rejectsAnyNodeNameOutsideTheVocabulary() {
        assertThatThrownBy(() -> DataSourceKey.valueOf("REPLICA_3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataSourceKey.valueOf("primary"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesAreStableBecauseTheyTravelInHeadersAndLogs() {
        assertThat(DataSourceKey.PRIMARY.name()).isEqualTo("PRIMARY");
        assertThat(DataSourceKey.REPLICA_1.name()).isEqualTo("REPLICA_1");
        assertThat(DataSourceKey.REPLICA_2.name()).isEqualTo("REPLICA_2");
    }
}
