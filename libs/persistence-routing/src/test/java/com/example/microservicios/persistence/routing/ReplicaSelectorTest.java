package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReplicaSelectorTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Alternates REPLICA_1 and REPLICA_2 in deterministic round-robin")
    void shouldAlternateReplicasInRoundRobin() {
        List<DataSourceKey> replicas = List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2);
        ReplicaSelector selector = new ReplicaSelector(() -> replicas, meterRegistry);

        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_1);
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_2);
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_1);
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_2);
    }

    @Test
    @DisplayName("Empty replica list returns PRIMARY and enters degraded mode")
    void shouldReturnPrimaryWhenReplicaListIsEmpty() {
        ReplicaSelector selector = new ReplicaSelector(Collections::emptyList, meterRegistry);

        DataSourceKey key = selector.select();

        assertThat(key).isEqualTo(DataSourceKey.PRIMARY);
        assertThat(selector.isDegraded()).isTrue();
        assertThat(meterRegistry.counter("db_routing_degraded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Adapts cursor correctly across health changes without out-of-bounds error")
    void shouldAdaptCursorAcrossHealthChanges() {
        AtomicReference<List<DataSourceKey>> healthState = new AtomicReference<>(
                List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2)
        );
        ReplicaSelector selector = new ReplicaSelector(healthState::get, meterRegistry);

        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_1);

        // Degrade to only REPLICA_2
        healthState.set(List.of(DataSourceKey.REPLICA_2));
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_2);
        assertThat(selector.select()).isEqualTo(DataSourceKey.REPLICA_2);

        // Recover both
        healthState.set(List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2));
        DataSourceKey next = selector.select();
        assertThat(next).isIn(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2);
    }

    @Test
    @DisplayName("Concurrent selections during health changes never throw index out of bounds")
    void shouldHandleConcurrentAccessAndHealthTransitionsWithoutError() throws InterruptedException {
        AtomicReference<List<DataSourceKey>> healthState = new AtomicReference<>(
                List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2)
        );
        ReplicaSelector selector = new ReplicaSelector(healthState::get, meterRegistry);

        int threads = 8;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        if (threadId == 0 && i % 100 == 0) {
                            if (healthState.get().size() == 2) {
                                healthState.set(List.of(DataSourceKey.REPLICA_1));
                            } else {
                                healthState.set(List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2));
                            }
                        }
                        DataSourceKey selected = selector.select();
                        if (selected == null) {
                            hasError.set(true);
                        }
                    }
                } catch (Throwable ex) {
                    hasError.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(hasError.get()).isFalse();
    }

    @Test
    @DisplayName("Maintains balanced load with maximum imbalance of 1 across 100 selections")
    void shouldMaintainMaxImbalanceOfOne() {
        List<DataSourceKey> replicas = List.of(DataSourceKey.REPLICA_1, DataSourceKey.REPLICA_2);
        ReplicaSelector selector = new ReplicaSelector(() -> replicas, meterRegistry);

        int count1 = 0;
        int count2 = 0;
        for (int i = 0; i < 100; i++) {
            DataSourceKey selected = selector.select();
            if (selected == DataSourceKey.REPLICA_1) count1++;
            if (selected == DataSourceKey.REPLICA_2) count2++;
        }

        assertThat(Math.abs(count1 - count2)).isLessThanOrEqualTo(1);
    }
}
