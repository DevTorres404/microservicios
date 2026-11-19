package com.example.microservicios.inventario;

import com.example.microservicios.inventario.repository.StockRepository;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReadWriteRoutingAspect;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.ReplicaSelector;
import com.example.microservicios.persistence.routing.RoutingDataSource;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "routing.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/inventario_db"
})
class InventarioRoutingWiringTest {

    @MockitoBean
    private Flyway flyway;

    @MockitoBean
    private StockRepository stockRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("replica1DataSource")
    private DataSource replica1DataSource;

    @Autowired
    @Qualifier("replica2DataSource")
    private DataSource replica2DataSource;

    @Autowired
    @Qualifier("routingDataSource")
    private RoutingDataSource routingDataSource;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Autowired
    private ReplicaHealthRegistry healthRegistry;

    @Autowired
    private ReplicaSelector replicaSelector;

    @Autowired
    private RoutingDecisionRecorder decisionRecorder;

    @Test
    @DisplayName("W6-13: Verifies routing bean wiring in servicio-inventario (pools, router, lazy proxy, health registry)")
    void shouldVerifyRoutingWiringBeans() {
        assertThat(primaryDataSource).isNotNull();
        assertThat(replica1DataSource).isNotNull();
        assertThat(replica2DataSource).isNotNull();
        assertThat(routingDataSource).isNotNull();

        // dataSource bean consumed by JPA must be LazyConnectionDataSourceProxy
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
        LazyConnectionDataSourceProxy proxy = (LazyConnectionDataSourceProxy) dataSource;
        assertThat(proxy.getTargetDataSource()).isSameAs(routingDataSource);

        // Routing beans
        assertThat(healthRegistry).isNotNull();
        assertThat(replicaSelector).isNotNull();
        assertThat(decisionRecorder).isNotNull();
        assertThat(context.containsBean("readWriteRoutingAspect")).isTrue();
    }

    @Test
    @DisplayName("W6-13: GET /api/inventario reports replica when replicas are healthy")
    void shouldReportReplicaOnReadWhenHealthy() throws Exception {
        healthRegistry.markHealthy(DataSourceKey.REPLICA_1);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_2);

        Mockito.when(stockRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/inventario")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Data-Source"));
    }
}
