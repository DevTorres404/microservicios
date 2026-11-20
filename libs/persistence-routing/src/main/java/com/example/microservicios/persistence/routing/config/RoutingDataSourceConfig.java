package com.example.microservicios.persistence.routing.config;

import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReadWriteRoutingAspect;
import com.example.microservicios.persistence.routing.ReplicaHealthRegistry;
import com.example.microservicios.persistence.routing.ReplicaSelector;
import com.example.microservicios.persistence.routing.RoutingDataSource;
import com.example.microservicios.persistence.routing.RoutingDecisionRecorder;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Declares the 3 physical connection pools (primary, replica1, replica2), the RoutingDataSource,
 * the health registry with periodic probe, the replica selector, and the primary LazyConnectionDataSourceProxy.
 *
 * <p>Rollback switch: {@code routing.enabled} (default {@code true}). When {@code false},
 * {@link ReadWriteRoutingAspect} is not registered and 100% of operations route directly to PRIMARY.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DataSourceProperties.class, RoutingProperties.class})
public class RoutingDataSourceConfig {

    @Bean("primaryDataSource")
    public HikariDataSource primaryDataSource(
            DataSourceProperties defaultProps,
            RoutingProperties routingProps,
            @Value("${spring.application.name:servicio}") String appName,
            @Value("${service.instance.id:${spring.application.name:servicio}:${INSTANCE_ID:${HOSTNAME:local}}}") String instanceId) {

        String url = routingProps.getNodes().getPrimary().getJdbcUrl();
        if (url == null || url.isBlank()) {
            url = defaultProps.getUrl();
        }
        String username = routingProps.getNodes().getPrimary().getUsername();
        if (username == null || username.isBlank()) {
            username = defaultProps.getUsername();
        }
        String password = routingProps.getNodes().getPrimary().getPassword();
        if (password == null || password.isBlank()) {
            password = defaultProps.getPassword();
        }
        int poolSize = routingProps.getNodes().getPrimary().getPoolSize();

        return HikariDataSourceFactory.create(
                appName + "-primary",
                url,
                username,
                password,
                poolSize,
                instanceId
        );
    }

    @Bean("replica1DataSource")
    public HikariDataSource replica1DataSource(
            DataSourceProperties defaultProps,
            RoutingProperties routingProps,
            @Value("${spring.application.name:servicio}") String appName,
            @Value("${service.instance.id:${spring.application.name:servicio}:${INSTANCE_ID:${HOSTNAME:local}}}") String instanceId) {

        String url = routingProps.getNodes().getReplica1().getJdbcUrl();
        if (url == null || url.isBlank()) {
            url = defaultProps.getUrl();
        }
        String username = routingProps.getNodes().getReplica1().getUsername();
        if (username == null || username.isBlank()) {
            username = defaultProps.getUsername();
        }
        String password = routingProps.getNodes().getReplica1().getPassword();
        if (password == null || password.isBlank()) {
            password = defaultProps.getPassword();
        }
        int poolSize = routingProps.getNodes().getReplica1().getPoolSize();

        return HikariDataSourceFactory.create(
                appName + "-replica-1",
                url,
                username,
                password,
                poolSize,
                instanceId
        );
    }

    @Bean("replica2DataSource")
    public HikariDataSource replica2DataSource(
            DataSourceProperties defaultProps,
            RoutingProperties routingProps,
            @Value("${spring.application.name:servicio}") String appName,
            @Value("${service.instance.id:${spring.application.name:servicio}:${INSTANCE_ID:${HOSTNAME:local}}}") String instanceId) {

        String url = routingProps.getNodes().getReplica2().getJdbcUrl();
        if (url == null || url.isBlank()) {
            url = defaultProps.getUrl();
        }
        String username = routingProps.getNodes().getReplica2().getUsername();
        if (username == null || username.isBlank()) {
            username = defaultProps.getUsername();
        }
        String password = routingProps.getNodes().getReplica2().getPassword();
        if (password == null || password.isBlank()) {
            password = defaultProps.getPassword();
        }
        int poolSize = routingProps.getNodes().getReplica2().getPoolSize();

        return HikariDataSourceFactory.create(
                appName + "-replica-2",
                url,
                username,
                password,
                poolSize,
                instanceId
        );
    }

    @Bean("replicaHealthRegistry")
    public ReplicaHealthRegistry replicaHealthRegistry(
            @Qualifier("replica1DataSource") DataSource replica1,
            @Qualifier("replica2DataSource") DataSource replica2,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        Map<DataSourceKey, DataSource> map = Map.of(
                DataSourceKey.REPLICA_1, replica1,
                DataSourceKey.REPLICA_2, replica2
        );
        ReplicaHealthRegistry registry = ReplicaHealthRegistry.forDataSources(map, meterRegistry);
        registry.startPeriodicProbing();
        return registry;
    }

    @Bean("replicaSelector")
    public ReplicaSelector replicaSelector(
            ReplicaHealthRegistry registry,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        return new ReplicaSelector(registry::getHealthyReplicas, meterRegistry);
    }

    @Bean("routingDecisionRecorder")
    public RoutingDecisionRecorder routingDecisionRecorder() {
        return new RoutingDecisionRecorder();
    }

    @Bean("readWriteRoutingAspect")
    @ConditionalOnProperty(name = "routing.enabled", havingValue = "true", matchIfMissing = true)
    public ReadWriteRoutingAspect readWriteRoutingAspect(
            ReplicaSelector replicaSelector,
            @Autowired(required = false) MeterRegistry meterRegistry,
            RoutingDecisionRecorder routingDecisionRecorder) {
        return new ReadWriteRoutingAspect(replicaSelector, meterRegistry, routingDecisionRecorder);
    }

    @Bean("routingDataSource")
    public RoutingDataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replica1DataSource") DataSource replica1,
            @Qualifier("replica2DataSource") DataSource replica2,
            ReplicaHealthRegistry healthRegistry) {
        return new RoutingDataSource(primary, replica1, replica2, healthRegistry);
    }

    @Bean("dataSource")
    @Primary
    public DataSource dataSource(@Qualifier("routingDataSource") RoutingDataSource routingDataSource) {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy(routingDataSource);
        proxy.setDefaultAutoCommit(true);
        proxy.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        proxy.afterPropertiesSet();
        return proxy;
    }
}
