package com.example.microservicios.pedidos;

import com.example.microservicios.pedidos.domain.Order;
import com.example.microservicios.pedidos.domain.OrderLineDraft;
import com.example.microservicios.pedidos.domain.OrderStatus;
import com.example.microservicios.pedidos.repository.OrderRepository;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.ReplicaSelector;
import com.example.microservicios.persistence.routing.RoutingDataSource;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
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
        "spring.datasource.url=jdbc:postgresql://localhost:5432/pedidos_db"
})
class PedidosRoutingWiringTest {

    @MockitoBean
    private Flyway flyway;

    @MockitoBean
    private OrderRepository orderRepository;

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
    @DisplayName("W6-14: Verifies routing bean wiring in servicio-pedidos (pools, router, lazy proxy, health registry)")
    void shouldVerifyRoutingWiringBeans() {
        assertThat(primaryDataSource).isNotNull();
        assertThat(replica1DataSource).isNotNull();
        assertThat(replica2DataSource).isNotNull();
        assertThat(routingDataSource).isNotNull();

        // dataSource bean consumed by JPA must be LazyConnectionDataSourceProxy
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
        LazyConnectionDataSourceProxy proxy = (LazyConnectionDataSourceProxy) dataSource;
        assertThat(proxy.getTargetDataSource()).isSameAs(routingDataSource);

        // Routing infrastructure beans
        assertThat(healthRegistry).isNotNull();
        assertThat(replicaSelector).isNotNull();
        assertThat(decisionRecorder).isNotNull();
        assertThat(context.containsBean("readWriteRoutingAspect")).isTrue();

        // EntityManagerFactoryDependsOnPostProcessor ordering Flyway before JPA
        assertThat(context.getBeansOfType(EntityManagerFactoryDependsOnPostProcessor.class)).isNotEmpty();
    }

    @Test
    @DisplayName("W6-14: GET /api/pedidos reports replica, and GET /api/pedidos/{id} reports PRIMARY (@CriticalRead)")
    void shouldRouteReadToReplicaAndCriticalToPrimary() throws Exception {
        healthRegistry.markHealthy(DataSourceKey.REPLICA_1);
        healthRegistry.markHealthy(DataSourceKey.REPLICA_2);

        Mockito.when(orderRepository.findAllWithLines()).thenReturn(List.of());

        Order order = Order.create("customer-1", List.of(new OrderLineDraft("SKU-1", 1)));
        setOrderId(order, 99L);
        Mockito.when(orderRepository.findById(99L)).thenReturn(Optional.of(order));

        // Read -> Replica
        mockMvc.perform(get("/api/pedidos")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Data-Source"));

        // Critical Read -> PRIMARY
        mockMvc.perform(get("/api/pedidos/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "PRIMARY"));
    }

    private void setOrderId(Order order, Long id) {
        try {
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
