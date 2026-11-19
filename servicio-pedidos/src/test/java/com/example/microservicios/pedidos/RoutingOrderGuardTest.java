package com.example.microservicios.pedidos;

import com.example.microservicios.persistence.routing.DataSourceContextHolder;
import com.example.microservicios.persistence.routing.DataSourceKey;
import com.example.microservicios.persistence.routing.ReadWriteRoutingAspect;
import com.example.microservicios.persistence.routing.ReplicaSelector;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(RoutingOrderGuardTest.TestConfig.class)
class RoutingOrderGuardTest {

    @Autowired
    private GuardedService guardedService;

    @Autowired
    private ReadWriteRoutingAspect routingAspect;

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("Guards interceptor ordering: aspect order (100) is internal to TransactionInterceptor order (50)")
    void shouldVerifyAspectRunsInsideTransaction() {
        // Interceptor ordering requirement: TransactionInterceptor (order=50) -> Aspect (order=100)
        assertThat(routingAspect.getOrderValue()).isGreaterThan(50);

        // In read-only transaction, transaction is active when aspect runs, routing to replica
        DataSourceKey readKey = guardedService.readOperation();
        assertThat(readKey).isEqualTo(DataSourceKey.REPLICA_1);

        // In write transaction, routes to primary
        DataSourceKey writeKey = guardedService.writeOperation();
        assertThat(writeKey).isEqualTo(DataSourceKey.PRIMARY);
    }

    public interface GuardedService {
        DataSourceKey readOperation();
        DataSourceKey writeOperation();
    }

    public static class GuardedServiceImpl implements GuardedService {

        @Override
        @Transactional(readOnly = true)
        public DataSourceKey readOperation() {
            // Verify that transaction is active
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            assertThat(txActive).isTrue();
            assertThat(readOnly).isTrue();
            return DataSourceContextHolder.peek();
        }

        @Override
        @Transactional
        public DataSourceKey writeOperation() {
            boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
            boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            assertThat(txActive).isTrue();
            assertThat(readOnly).isFalse();
            return DataSourceContextHolder.peek();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement(order = 50)
    static class TestConfig {

        @Bean
        public DataSource dataSource() throws Exception {
            DataSource ds = Mockito.mock(DataSource.class);
            java.sql.Connection conn = Mockito.mock(java.sql.Connection.class);
            Mockito.when(ds.getConnection()).thenReturn(conn);
            return ds;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public ReplicaSelector replicaSelector() {
            return new ReplicaSelector(() -> List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2));
        }

        @Bean
        public ReadWriteRoutingAspect readWriteRoutingAspect(ReplicaSelector replicaSelector) {
            return new ReadWriteRoutingAspect(replicaSelector);
        }

        @Bean
        public GuardedService guardedService() {
            return new GuardedServiceImpl();
        }
    }
}
