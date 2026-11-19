package com.example.microservicios.pedidos;

import com.example.microservicios.pedidos.repository.OrderRepository;
import com.example.microservicios.pedidos.web.PedidoController;
import com.example.microservicios.pedidos.web.PedidosExceptionHandler;
import com.example.microservicios.persistence.routing.DataSourceContextHolder;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.DbNodeUnavailableException;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.ReplicaSelector;
import com.example.microservicios.persistence.routing.RoutingDataSource;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@Import(PedidosExceptionHandler.class)
class NodeDemotionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("Immediate demotion occurs when pool fails to loan connection, and controller returns 503 DB_NODE_UNAVAILABLE")
    void shouldDemoteNodeAndReturn503WhenPoolFails() throws Exception {
        DataSource primary = Mockito.mock(DataSource.class);
        DataSource replica1 = Mockito.mock(DataSource.class);
        DataSource replica2 = Mockito.mock(DataSource.class);
        ReplicaHealthRegistry healthRegistry = new ReplicaHealthRegistry(k -> null, null);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_1);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_2);

        assertThat(healthRegistry.isAvailable(DataSourceKey.REPLICA_1)).isTrue();

        Mockito.when(replica1.getConnection()).thenThrow(new SQLException("Connection refused"));

        RoutingDataSource routingDataSource = new RoutingDataSource(primary, replica1, replica2, healthRegistry);

        DataSourceContextHolder.push(DataSourceKey.REPLICA_1);
        assertThatThrownBy(routingDataSource::getConnection)
                .isInstanceOf(DbNodeUnavailableException.class);

        // Verification 1: REPLICA_1 is immediately demoted (excluded)
        assertThat(healthRegistry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();
        assertThat(healthRegistry.getLastFailureCause(DataSourceKey.REPLICA_1)).isEqualTo("connection-refused");

        // Verification 2: Next selection excludes REPLICA_1 and picks REPLICA_2
        ReplicaSelector selector = new ReplicaSelector(healthRegistry::getHealthyReplicas, null);
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_2);

        // Verification 3: Exception returns RFC 9457 ProblemDetail 503 DB_NODE_UNAVAILABLE
        Mockito.when(orderRepository.findAllWithLines()).thenThrow(new DbNodeUnavailableException("Database node unavailable: REPLICA_1"));

        mockMvc.perform(get("/api/pedidos")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("DB_NODE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Database node unavailable: REPLICA_1"));
    }
}
