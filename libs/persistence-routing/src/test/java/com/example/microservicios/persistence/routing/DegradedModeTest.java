package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DegradedModeTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("When all replicas are DOWN, 100% of read requests route to PRIMARY")
    void shouldRouteAllReadsToPrimaryWhenReplicasDown() {
        ReplicaSelector selector = new ReplicaSelector(Collections::emptyList, meterRegistry);

        for (int i = 0; i < 50; i++) {
            DataSourceKey key = selector.select();
            assertThat(key).isEqualTo(DataSourceKey.PRIMARY);
        }

        // Degraded mode entered once
        assertThat(selector.isDegraded()).isTrue();
        assertThat(meterRegistry.counter("db_routing_degraded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Increments db_routing_degraded_total only once per transition to degraded mode")
    void shouldIncrementDegradedCounterOnlyOncePerTransition() {
        AtomicReference<List<DataSourceKey>> replicas = new AtomicReference<>(Collections.emptyList());
        ReplicaSelector selector = new ReplicaSelector(replicas::get, meterRegistry);

        // 1st entry to degraded mode
        selector.select();
        selector.select();
        assertThat(meterRegistry.counter("db_routing_degraded_total").count()).isEqualTo(1.0);

        // Recover
        replicas.set(List.of(DataSourceKey.REPLICA_1));
        selector.select();
        assertThat(selector.isDegraded()).isFalse();

        // 2nd entry to degraded mode
        replicas.set(Collections.emptyList());
        selector.select();
        assertThat(selector.isDegraded()).isTrue();
        assertThat(meterRegistry.counter("db_routing_degraded_total").count()).isEqualTo(2.0);
    }
}
