package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadWriteRoutingAspectTest {

    private MeterRegistry meterRegistry;
    private ReplicaSelector replicaSelector;
    private ReadWriteRoutingAspect aspect;

    interface TestService {
        DataSourceKey writeOperation();
        DataSourceKey readOnlyOperation();
        DataSourceKey criticalReadOnlyOperation();
        DataSourceKey criticalWithoutTransactionOperation();
        DataSourceKey plainOperation();
    }

    static class TestServiceImpl implements TestService {

        @Override
        @Transactional
        public DataSourceKey writeOperation() {
            return DataSourceContextHolder.peek();
        }

        @Override
        @Transactional(readOnly = true)
        public DataSourceKey readOnlyOperation() {
            return DataSourceContextHolder.peek();
        }

        @Override
        @Transactional(readOnly = true)
        @CriticalRead
        public DataSourceKey criticalReadOnlyOperation() {
            return DataSourceContextHolder.peek();
        }

        @Override
        @CriticalRead
        public DataSourceKey criticalWithoutTransactionOperation() {
            return DataSourceContextHolder.peek();
        }

        @Override
        public DataSourceKey plainOperation() {
            DataSourceKey key = DataSourceContextHolder.peek();
            return key != null ? key : DataSourceKey.PRIMARY;
        }
    }

    private TestService proxy;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        replicaSelector = new ReplicaSelector(
                () -> List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2),
                meterRegistry
        );
        aspect = new ReadWriteRoutingAspect(replicaSelector, meterRegistry);

        AspectJProxyFactory factory = new AspectJProxyFactory(new TestServiceImpl());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("Case 1: @Transactional with readOnly=false routes to PRIMARY")
    void shouldRouteWriteToPrimary() {
        DataSourceKey decided = proxy.writeOperation();

        assertThat(decided).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Case 2: @Transactional(readOnly=true) routes to replica via ReplicaSelector")
    void shouldRouteReadOnlyToReplica() {
        DataSourceKey decided1 = proxy.readOnlyOperation();
        assertThat(decided1).isEqualTo(DataSourceKey.REPLICA_1);
        assertThat(DataSourceContextHolder.peek()).isNull();

        DataSourceKey decided2 = proxy.readOnlyOperation();
        assertThat(decided2).isEqualTo(DataSourceKey.REPLICA_2);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Case 3: @Transactional(readOnly=true) + @CriticalRead routes to PRIMARY")
    void shouldRouteCriticalReadOnlyToPrimary() {
        DataSourceKey decided = proxy.criticalReadOnlyOperation();

        assertThat(decided).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Case 4: @CriticalRead without transaction routes to PRIMARY")
    void shouldRouteCriticalWithoutTxToPrimary() {
        DataSourceKey decided = proxy.criticalWithoutTransactionOperation();

        assertThat(decided).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Case 5: Without transaction and without annotation routes to PRIMARY")
    void shouldRoutePlainToPrimary() {
        DataSourceKey decided = proxy.plainOperation();

        assertThat(decided).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }

    @Test
    @DisplayName("Case 6: Read-only operation with all replicas DOWN routes to PRIMARY (degraded mode)")
    void shouldRouteToPrimaryWhenAllReplicasDown() {
        ReplicaSelector emptySelector = new ReplicaSelector(Collections::emptyList, meterRegistry);
        ReadWriteRoutingAspect degradedAspect = new ReadWriteRoutingAspect(emptySelector, meterRegistry);

        AspectJProxyFactory factory = new AspectJProxyFactory(new TestServiceImpl());
        factory.setProxyTargetClass(true);
        factory.addAspect(degradedAspect);
        TestService degradedProxy = factory.getProxy();

        DataSourceKey decided = degradedProxy.readOnlyOperation();

        assertThat(decided).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(DataSourceContextHolder.peek()).isNull();
    }
}
