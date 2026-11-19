package com.example.microservicios.pedidos;

import com.example.microservicios.persistence.routing.DataSourceContextHolder;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReadWriteRoutingAspect;
import com.example.microservicios.persistence.routing.RoutingDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "routing.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/pedidos_db"
})
class RoutingDisabledTest {

    @MockitoBean
    private Flyway flyway;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("When routing.enabled=false, ReadWriteRoutingAspect is not registered as a bean")
    void shouldNotRegisterRoutingAspectWhenDisabled() {
        assertThatThrownBy(() -> context.getBean(ReadWriteRoutingAspect.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    @DisplayName("When routing.enabled=false, all required datasource beans still exist and routing resolves to PRIMARY")
    void shouldRetainDataSourcesAndDefaultToPrimaryWhenRoutingDisabled() {
        assertThat(context.containsBean("primaryDataSource")).isTrue();
        assertThat(context.containsBean("replica1DataSource")).isTrue();
        assertThat(context.containsBean("replica2DataSource")).isTrue();
        assertThat(context.containsBean("routingDataSource")).isTrue();
        assertThat(context.containsBean("dataSource")).isTrue();

        RoutingDataSource rds = context.getBean(RoutingDataSource.class);
        // With aspect absent, context holder remains null -> RoutingDataSource resolves to PRIMARY
        assertThat(DataSourceContextHolder.peek()).isNull();
        assertThat(rds.determineCurrentLookupKey()).isEqualTo(DataSourceKey.PRIMARY);
    }
}
