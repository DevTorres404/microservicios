package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingMetricsTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RoutingDecisionRecorder recorder = new RoutingDecisionRecorder();

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
        recorder.clear();
    }

    interface MetricTarget {
        void write();
        void read();
        void critical();
    }

    static class MetricTargetImpl implements MetricTarget {
        @Override
        @Transactional
        public void write() {}

        @Override
        @Transactional(readOnly = true)
        public void read() {}

        @Override
        @Transactional(readOnly = true)
        @CriticalRead
        public void critical() {}
    }

    @Test
    @DisplayName("Publishes db_routing_total, critical_read_total, and captures in RoutingDecisionRecorder")
    void shouldPublishMetricsAndCaptureDecisions() {
        Map<DataSourceKey, HealthProbeResult> probeResults = Map.of(
                DataSourceKey.REPLICA_1, HealthProbeResult.success(5),
                DataSourceKey.REPLICA_2, HealthProbeResult.success(5)
        );
        ReplicaHealthRegistry healthRegistry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        healthRegistry.probeNow();
        healthRegistry.probeNow();

        ReplicaSelector selector = new ReplicaSelector(healthRegistry::getHealthyReplicas, meterRegistry);
        ReadWriteRoutingAspect aspect = new ReadWriteRoutingAspect(selector, meterRegistry, recorder);

        AspectJProxyFactory factory = new AspectJProxyFactory(new MetricTargetImpl());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        MetricTarget proxy = factory.getProxy();

        proxy.write();
        proxy.read();
        proxy.critical();

        // 1. db_routing_total
        double writes = meterRegistry.counter("db_routing_total", "node", "PRIMARY", "outcome", "success").count();
        double reads = meterRegistry.counter("db_routing_total", "node", "REPLICA_1", "outcome", "success").count();
        assertThat(writes).isEqualTo(2.0); // 1 write + 1 critical read
        assertThat(reads).isEqualTo(1.0);  // 1 read-only

        // 2. critical_read_total
        double criticalReads = meterRegistry.counter("critical_read_total").count();
        assertThat(criticalReads).isEqualTo(1.0);

        // 3. replica_health
        assertThat(meterRegistry.get("replica_health").tag("node", "REPLICA_1").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("replica_health").tag("node", "REPLICA_2").gauge().value()).isEqualTo(1.0);

        // 4. RoutingDecisionRecorder
        assertThat(recorder.decisions()).containsExactly(
                DataSourceKey.PRIMARY,
                DataSourceKey.REPLICA_1,
                DataSourceKey.PRIMARY
        );
        assertThat(recorder.lastDecision()).isEqualTo(DataSourceKey.PRIMARY);
    }
}
