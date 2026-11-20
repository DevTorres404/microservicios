package com.example.microservicios.persistence.routing;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic round-robin selector over an immutable snapshot of healthy replicas.
 * Uses Math.floorMod on an AtomicInteger cursor.
 * When no healthy replicas are available, falls back to PRIMARY (degraded mode),
 * logs a single WARN upon entry and increments db_routing_degraded_total.
 */
public class ReplicaSelector {

    private static final Logger log = LoggerFactory.getLogger(ReplicaSelector.class);

    private final Supplier<List<DataSourceKey>> healthyReplicasSupplier;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public ReplicaSelector(Supplier<List<DataSourceKey>> healthyReplicasSupplier) {
        this(healthyReplicasSupplier, null);
    }

    public ReplicaSelector(Supplier<List<DataSourceKey>> healthyReplicasSupplier, MeterRegistry meterRegistry) {
        this.healthyReplicasSupplier = healthyReplicasSupplier;
        this.meterRegistry = meterRegistry;
    }

    public DataSourceKey select() {
        List<DataSourceKey> snapshot = healthyReplicasSupplier.get();
        if (snapshot == null || snapshot.isEmpty()) {
            if (degraded.compareAndSet(false, true)) {
                log.warn("Entering degraded persistence routing mode: zero replicas healthy, routing reads to PRIMARY");
                if (meterRegistry != null) {
                    meterRegistry.counter("db_routing_degraded_total").increment();
                }
            }
            return DataSourceKey.PRIMARY;
        }

        if (degraded.compareAndSet(true, false)) {
            log.info("Exiting degraded persistence routing mode: healthy replicas detected");
        }

        int size = snapshot.size();
        int index = Math.floorMod(cursor.getAndIncrement(), size);
        return snapshot.get(index);
    }

    public boolean isDegraded() {
        return degraded.get();
    }

    public int getCursor() {
        return cursor.get();
    }
}
