package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicaHealthRegistryTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Transitions UP -> DOWN when probe times out or fails")
    void shouldTransitionUpToDownOnProbeFailure() {
        Map<DataSourceKey, HealthProbeResult> probeResults = new HashMap<>();
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.success(5));
        probeResults.put(DataSourceKey.REPLICA_2, HealthProbeResult.success(5));

        ReplicaHealthRegistry registry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        // Two consecutive successes to bring both UP
        registry.probeNow();
        registry.probeNow();
        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isTrue();
        assertThat(registry.getHealthyReplicas()).contains(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2);

        // Fail REPLICA_1 with timeout
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.failure("probe-timeout", 1005));
        registry.probeNow();

        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();
        assertThat(registry.getState(DataSourceKey.REPLICA_1)).isEqualTo(ReplicaHealthState.DOWN);
        assertThat(registry.getLastFailureCause(DataSourceKey.REPLICA_1)).isEqualTo("probe-timeout");
        assertThat(registry.getHealthyReplicas()).containsExactly(DataSourceKey.REPLICA_2);
    }

    @Test
    @DisplayName("Transitions DOWN -> PROBING with 1 success, and PROBING -> UP with 2 consecutive successes")
    void shouldTransitionDownToProbingToUpWithTwoSuccesses() {
        Map<DataSourceKey, HealthProbeResult> probeResults = new HashMap<>();
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.failure("connection-refused", 10));

        ReplicaHealthRegistry registry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        registry.probeNow();
        assertThat(registry.getState(DataSourceKey.REPLICA_1)).isEqualTo(ReplicaHealthState.DOWN);
        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();

        // 1st success -> PROBING (still excluded from healthy list)
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.success(10));
        registry.probeNow();
        assertThat(registry.getState(DataSourceKey.REPLICA_1)).isEqualTo(ReplicaHealthState.PROBING);
        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();
        assertThat(registry.getHealthyReplicas()).doesNotContain(DataSourceKey.REPLICA_1);

        // 2nd consecutive success -> UP (re-included in healthy list)
        registry.probeNow();
        assertThat(registry.getState(DataSourceKey.REPLICA_1)).isEqualTo(ReplicaHealthState.UP);
        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isTrue();
        assertThat(registry.getHealthyReplicas()).contains(DataSourceKey.REPLICA_1);
    }

    @Test
    @DisplayName("Excludes replica when wal_receiver status is not streaming")
    void shouldExcludeReplicaWhenNotStreaming() {
        Map<DataSourceKey, HealthProbeResult> probeResults = new HashMap<>();
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.failure("not-streaming: catchup", 15));

        ReplicaHealthRegistry registry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        registry.probeNow();

        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();
        assertThat(registry.getLastFailureCause(DataSourceKey.REPLICA_1)).contains("not-streaming");
    }

    @Test
    @DisplayName("Immediate demotion marks node DOWN without waiting for next scheduled probe")
    void shouldDemoteNodeImmediatelyOnMarkDown() {
        Map<DataSourceKey, HealthProbeResult> probeResults = new HashMap<>();
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.success(5));

        ReplicaHealthRegistry registry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        registry.probeNow();
        registry.probeNow();
        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isTrue();

        registry.markDown(DataSourceKey.REPLICA_1, "connection-refused");

        assertThat(registry.isAvailable(DataSourceKey.REPLICA_1)).isFalse();
        assertThat(registry.getState(DataSourceKey.REPLICA_1)).isEqualTo(ReplicaHealthState.DOWN);
        assertThat(registry.getLastFailureCause(DataSourceKey.REPLICA_1)).isEqualTo("connection-refused");
    }

    @Test
    @DisplayName("Publishes replica_health gauge in Micrometer")
    void shouldPublishHealthGauge() {
        Map<DataSourceKey, HealthProbeResult> probeResults = new HashMap<>();
        probeResults.put(DataSourceKey.REPLICA_1, HealthProbeResult.success(5));
        probeResults.put(DataSourceKey.REPLICA_2, HealthProbeResult.failure("down", 10));

        ReplicaHealthRegistry registry = new ReplicaHealthRegistry(probeResults::get, meterRegistry);
        registry.probeNow();
        registry.probeNow();

        double gauge1 = meterRegistry.get("replica_health").tag("node", "REPLICA_1").gauge().value();
        double gauge2 = meterRegistry.get("replica_health").tag("node", "REPLICA_2").gauge().value();

        assertThat(gauge1).isEqualTo(1.0);
        assertThat(gauge2).isEqualTo(0.0);
    }
}
